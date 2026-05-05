package com.example.inventory.commons.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;

/**
 * MyBatis インターセプタ。文の prepare 直前に PostgreSQL の {@code search_path} を
 * 現テナントのスキーマに切り替える(Bridge方式マルチテナンシ、ADR-0003)。
 *
 * <p>Spring の {@link org.springframework.stereotype.Component} として登録し、 SqlSessionFactory
 * に自動でプラグインとして組み込まれる。
 *
 * <p><b>セキュリティクリティカル。</b> このコードのバグはテナント間データ漏洩を引き起こす。 {@code commons-test} の統合テストでカバレッジを必須とする。
 */
@Intercepts({
    @Signature(
            type = StatementHandler.class,
            method = "prepare",
            args = {Connection.class, Integer.class})
})
public class TenantSearchPathInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Connection connection = (Connection) invocation.getArgs()[0];
        TenantId tenant = TenantContext.required();
        applySearchPath(connection, tenant);
        return invocation.proceed();
    }

    private void applySearchPath(Connection connection, TenantId tenant) throws SQLException {
        // スキーマ名は TenantId のregexで制約済み → 文字列補間してもSQLインジェクションのリスクなし。
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET LOCAL search_path TO " + tenant.schemaName() + ", public");
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 設定なし
    }
}
