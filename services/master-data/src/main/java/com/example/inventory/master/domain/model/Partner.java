package com.example.inventory.master.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.inventory.commons.event.DomainEvent;
import com.example.inventory.master.domain.event.PartnerCreatedEvent;

/**
 * Partner 集約ルート。テナント内の取引先(顧客 / 仕入先 / 配送業者)1 件を表す。
 *
 * <p>純粋な POJO(ADR-0009)。MyBatis/JPA のアノテーション無し。 楽観ロック用の {@code version} を持ち、save 時は +1
 * される(commons-persistence 規約)。
 */
public final class Partner {

    private final PartnerId id;
    private final PartnerCode code;
    private String name;
    private String partnerType;
    private String contactEmail;
    private long version;

    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    public static Partner restore(
            PartnerId id,
            PartnerCode code,
            String name,
            String partnerType,
            String contactEmail,
            long version) {
        return new Partner(id, code, name, partnerType, contactEmail, version);
    }

    /** 新規 Partner を作成する。{@link PartnerCreatedEvent} を発行する。 */
    public static Partner create(
            PartnerId id, PartnerCode code, String name, String partnerType, String contactEmail) {
        Partner partner = new Partner(id, code, name, partnerType, contactEmail, 0L);
        partner.pendingEvents.add(
                new PartnerCreatedEvent(
                        id.value(),
                        code.value(),
                        name,
                        partner.partnerType,
                        partner.contactEmail,
                        partner.version + 1,
                        java.time.Instant.now()));
        return partner;
    }

    private Partner(
            PartnerId id,
            PartnerCode code,
            String name,
            String partnerType,
            String contactEmail,
            long version) {
        if (name == null || name.isBlank() || name.length() > 200) {
            throw new IllegalArgumentException("Partner 名が不正(空 or 200 文字超過)");
        }
        this.id = id;
        this.code = code;
        this.name = name;
        this.partnerType = partnerType == null ? "" : partnerType;
        this.contactEmail = contactEmail == null ? "" : contactEmail;
        this.version = version;
    }

    public PartnerId id() {
        return id;
    }

    public PartnerCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public String partnerType() {
        return partnerType;
    }

    public String contactEmail() {
        return contactEmail;
    }

    public long version() {
        return version;
    }

    public List<DomainEvent> pendingEvents() {
        return Collections.unmodifiableList(pendingEvents);
    }

    public void clearPendingEvents() {
        pendingEvents.clear();
    }
}
