package com.example.inventory.commons.audit;

import java.lang.reflect.Method;
import java.time.Instant;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.inventory.commons.error.BusinessException;
import com.example.inventory.commons.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@link Auditable} 付与メソッドを横取りして監査イベントを発行するアスペクト (ADR-0008)。
 *
 * <p>処理の流れ:
 *
 * <ol>
 *   <li>呼出前: 操作者ID / テナントID / トレースID を捕捉。 targetId を SpEL で引数から評価。第1引数を JSON 化。
 *   <li>メソッド実行。
 *   <li>呼出後(success / business / system 例外を区別): {@link AuditEvent} を組み立て、 {@link AuditEventEmitter}
 *       経由で別トランザクションで発行。元の例外は再送出。
 * </ol>
 *
 * <p>SpEL 評価には Java の {@code -parameters} コンパイルフラグでパラメータ名が 保存されている前提。ビルド設定(commons-bom の compiler
 * 設定)で有効化済み。
 */
@Aspect
public class AuditableAspect {

    private static final Logger LOG = LoggerFactory.getLogger(AuditableAspect.class);

    private final AuditEventEmitter emitter;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    public AuditableAspect(AuditEventEmitter emitter, ObjectMapper baseObjectMapper) {
        this.emitter = emitter;
        // audit ペイロード専用に基底 ObjectMapper をコピーし、@AuditMask による機微情報マスキングを有効化。
        // 通常 API レスポンス用の ObjectMapper には影響しない(コピーなので独立)。
        this.objectMapper = baseObjectMapper.copy().registerModule(new AuditMaskingModule());
    }

    @Around("@annotation(auditable)")
    public Object around(ProceedingJoinPoint pjp, Auditable auditable) throws Throwable {
        Instant occurredAt = Instant.now();
        String operator = currentOperator();
        String tenant = currentTenant();
        String targetId = evaluateTargetId(pjp, auditable);
        String inputJson = serializeFirstArg(pjp.getArgs());

        AuditOutcome outcome = AuditOutcome.SUCCESS;
        String errorCode = null;
        try {
            return pjp.proceed();
        } catch (BusinessException e) {
            outcome = AuditOutcome.BUSINESS_FAILURE;
            errorCode = e.errorCode();
            throw e;
        } catch (Throwable t) {
            outcome = AuditOutcome.SYSTEM_FAILURE;
            errorCode = "SYSTEM_ERROR";
            throw t;
        } finally {
            try {
                emitter.emit(
                        new AuditEvent(
                                auditable.action(),
                                auditable.targetType(),
                                targetId,
                                operator,
                                tenant,
                                outcome,
                                errorCode,
                                auditable.read(),
                                inputJson,
                                occurredAt));
            } catch (RuntimeException emitFailure) {
                // 監査発行が落ちても業務は通す(ただし大きな問題なのでアラート出すこと)。
                // 補完策: ArchUnit + DBA人的ルール + WAL別保管(ADR-0008)。
                LOG.error(
                        "監査イベントの発行に失敗 action={} target={}/{}: {}",
                        auditable.action(),
                        auditable.targetType(),
                        targetId,
                        emitFailure.toString());
            }
        }
    }

    private String evaluateTargetId(ProceedingJoinPoint pjp, Auditable auditable) {
        String expr = auditable.targetIdExpression();
        if (expr == null || expr.isEmpty()) {
            return "";
        }
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        String[] names = parameterNameDiscoverer.getParameterNames(method);
        Object[] args = pjp.getArgs();

        StandardEvaluationContext ctx = new StandardEvaluationContext();
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        // 引数名が取れなかった場合のフォールバック: #a0, #a1, ... で参照可能に。
        for (int i = 0; i < args.length; i++) {
            ctx.setVariable("a" + i, args[i]);
        }

        try {
            Expression parsed = expressionParser.parseExpression(expr);
            Object value = parsed.getValue(ctx);
            return value == null ? "" : value.toString();
        } catch (RuntimeException e) {
            LOG.warn("targetIdExpression の評価に失敗 expr={}: {}", expr, e.toString());
            return "";
        }
    }

    private String serializeFirstArg(Object[] args) {
        if (args == null || args.length == 0 || args[0] == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(args[0]);
            if (json.length() > AuditEvent.INPUT_JSON_LIMIT) {
                return json.substring(0, AuditEvent.INPUT_JSON_LIMIT) + "...<truncated>";
            }
            return json;
        } catch (JsonProcessingException e) {
            LOG.warn("監査の入力引数シリアライズに失敗: {}", e.toString());
            return null;
        }
    }

    private static String currentOperator() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return auth.getName();
    }

    private static String currentTenant() {
        try {
            return TenantContext.required().value();
        } catch (IllegalStateException e) {
            // テナント未設定でも監査は残したい(認証前のエンドポイント等)。
            return "unknown";
        }
    }
}
