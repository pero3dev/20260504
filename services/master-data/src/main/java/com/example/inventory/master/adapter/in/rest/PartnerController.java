package com.example.inventory.master.adapter.in.rest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.example.inventory.master.adapter.in.rest.api.PartnersApi;
import com.example.inventory.master.adapter.in.rest.api.model.CreatePartnerRequest;
import com.example.inventory.master.adapter.in.rest.api.model.PartnerResponse;
import com.example.inventory.master.application.port.in.CreatePartnerUseCase;
import com.example.inventory.master.application.port.in.GetPartnerUseCase;
import com.example.inventory.master.domain.model.Partner;

/** Partner REST 入力アダプタ。OpenAPI スキーマから生成された {@link PartnersApi} を実装する(ADR-0006)。 */
@RestController
public class PartnerController implements PartnersApi {

    private final CreatePartnerUseCase createPartner;
    private final GetPartnerUseCase getPartner;

    public PartnerController(CreatePartnerUseCase createPartner, GetPartnerUseCase getPartner) {
        this.createPartner = createPartner;
        this.getPartner = getPartner;
    }

    @Override
    public ResponseEntity<PartnerResponse> createPartner(CreatePartnerRequest request) {
        Partner created =
                createPartner.create(
                        new CreatePartnerUseCase.Command(
                                request.getCode(),
                                request.getName(),
                                request.getPartnerType(),
                                request.getContactEmail()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(created));
    }

    @Override
    public ResponseEntity<PartnerResponse> getPartner(Long partnerId) {
        return ResponseEntity.ok(toResponse(getPartner.get(partnerId)));
    }

    private static PartnerResponse toResponse(Partner partner) {
        PartnerResponse r = new PartnerResponse();
        r.setId(partner.id().value());
        r.setCode(partner.code().value());
        r.setName(partner.name());
        r.setPartnerType(partner.partnerType());
        r.setContactEmail(partner.contactEmail());
        r.setVersion(partner.version() + 1);
        return r;
    }
}
