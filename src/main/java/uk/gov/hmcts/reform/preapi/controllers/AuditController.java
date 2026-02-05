package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.data.web.SortDefault;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchAudits;
import uk.gov.hmcts.reform.preapi.dto.AuditDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.AuditService;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;

import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

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
    public ResponseEntity<Void> upsertAudit(@RequestHeader HttpHeaders headers,
                                            @PathVariable UUID id,
                                            @Valid @RequestBody CreateAuditDTO createAuditDTO) {
        if (!id.equals(createAuditDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createAuditDTO.id");
        }

        var userId = headers.getValuesAsList(X_USER_ID_HEADER).isEmpty()
            ? null
            : UUID.fromString(headers.getValuesAsList(X_USER_ID_HEADER).getFirst());
        this.auditService.upsert(createAuditDTO, userId);
        return new ResponseEntity<>(HttpStatus.CREATED);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    @Operation(operationId = "getAudit", summary = "Get an Audit Entry")
    public HttpEntity<AuditDTO> getAuditById(@PathVariable UUID id) {
        return ResponseEntity.ok(auditService.findById(id));
    }

    @GetMapping
    @Operation(operationId = "getAuditLogs", summary = "Search all Audits")
    @Parameter(
        name = "after",
        description = "The date time to search after",
        schema = @Schema(implementation = LocalDateTime.class, format = "iso-date-time"),
        example = "2021-01-01T00:00:00"
    )
    @Parameter(
        name = "before",
        description = "The date time to search before",
        schema = @Schema(implementation = LocalDateTime.class, format = "iso-date-time"),
        example = "2021-01-01T00:00:00"
    )
    @Parameter(
        name = "functionalArea",
        description = "The functional area to search by",
        schema = @Schema(implementation = String.class),
        example = "API"
    )
    @Parameter(
        name = "source",
        description = "The source to search by",
        schema = @Schema(implementation = AuditLogSource.class)
    )
    @Parameter(
        name = "userName",
        description = "Partial user's name to search by",
        schema = @Schema(implementation = String.class)
    )
    @Parameter(
        name = "courtId",
        description = "The court id of the audit to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "caseReference",
        description = "The case reference of the audit to search by",
        schema = @Schema(implementation = String.class),
        example = "CASE12345"
    )
    @Parameter(
        name = "page",
        description = "The page number of search result to return",
        schema = @Schema(implementation = Integer.class),
        example = "0"
    )
    @Parameter(
        name = "size",
        description = "The number of search results to return per page",
        schema = @Schema(implementation = Integer.class),
        example = "10"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER')")
    public HttpEntity<PagedModel<EntityModel<AuditDTO>>> searchAuditLogs(
        @Parameter(hidden = true) @ModelAttribute SearchAudits params,
        @SortDefault.SortDefaults(
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
        )
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<AuditDTO> assembler
    ) {
        var resultPage = auditService.findAll(
            params.getAfter() != null ? Timestamp.valueOf(params.getAfter()) : null,
            params.getBefore() != null ? Timestamp.valueOf(params.getBefore()) : null,
            params.getFunctionalArea(),
            params.getSource(),
            params.getUserName(),
            params.getCourtId(),
            params.getCaseReference(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }
}
