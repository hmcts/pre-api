package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.AuditService;

import java.util.UUID;

@RestController
@RequestMapping("/audit")
public class AuditController {

    private final AuditService auditService;

    @Autowired
    public AuditController(AuditService auditService) {
        super();
        this.auditService = auditService;
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putAudit", summary = "Create an Audit Entry")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> upsertCase(@RequestHeader("X-User-Id") UUID xUserId,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody CreateAuditDTO createAuditDTO) {
        if (!id.equals(createAuditDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createAuditDTO.id");
        }
        this.auditService.upsert(createAuditDTO, xUserId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
