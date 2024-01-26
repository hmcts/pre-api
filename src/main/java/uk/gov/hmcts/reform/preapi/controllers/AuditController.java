package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.AuditService;

import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController extends PreApiController {

    private final AuditService auditService;

    @Autowired
    public AuditController(AuditService auditService) {
        super();
        this.auditService = auditService;
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putAudit", summary = "Create an Audit Entry")
    public ResponseEntity<Void> upsertCase(@RequestHeader("X-User-Id") UUID xUserId,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody CreateAuditDTO createAuditDTO) {
        if (!id.equals(createAuditDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createAuditDTO.id");
        }
        return getUpsertResponse(auditService.upsert(createAuditDTO, xUserId), createAuditDTO.getId());
    }
}
