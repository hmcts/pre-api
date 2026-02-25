package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@RestController
@RequestMapping("/audit")
@SuppressWarnings("PMD.LooseCoupling") //We need HttpHeaders as it is case-insensitive
public class AuditController {

    private final AuditService auditService;

    @Autowired
    public AuditController(AuditService auditService) {
        super();
        this.auditService = auditService;
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putAudit", summary = "Create an Audit Entry")
    public ResponseEntity<Void> upsertAudit(@RequestHeader HttpHeaders headers,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody CreateAuditDTO createAuditDTO) {
        if (!id.equals(createAuditDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createAuditDTO.id");
        }

        UUID userId = headers.getValuesAsList(X_USER_ID_HEADER).isEmpty()
            ? null
            : UUID.fromString(headers.getValuesAsList(X_USER_ID_HEADER).getFirst());
        this.auditService.upsert(createAuditDTO, userId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }
}
