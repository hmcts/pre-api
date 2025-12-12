package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchCases;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.exception.RequestedPageOutOfRangeException;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController extends PreApiController {

    private final CaseService caseService;

    @Autowired
    public CaseController(CaseService caseService) {
        super();
        this.caseService = caseService;
    }

    @GetMapping("/{id}")
    @Operation(operationId = "getCaseById", summary = "Get a case by id")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaseDTO> getCaseById(@PathVariable(name = "id") UUID caseId) {
        return ResponseEntity.ok(caseService.findById(caseId));
    }

    @GetMapping
    @Operation(operationId = "getCases", summary = "Get a case by reference or court id")
    @Parameter(
        name = "reference",
        description = "The case reference to search by",
        schema = @Schema(implementation = String.class),
        example = "1234567890123456"
    )
    @Parameter(
        name = "courtId",
        description = "The court id to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "includeDeleted",
        description = "Include cases marked as deleted",
        schema = @Schema(implementation = Boolean.class)
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
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public HttpEntity<PagedModel<EntityModel<CaseDTO>>> getCases(
        @Parameter(hidden = true) @ModelAttribute SearchCases params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<CaseDTO> assembler
    ) {
        Page<CaseDTO> resultPage = caseService.searchBy(
            params.getReference(),
            params.getCourtId(),
            params.getIncludeDeleted() != null && params.getIncludeDeleted(),
            pageable
        );

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putCase", summary = "Create or Update a Case")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> upsertCase(@PathVariable UUID id, @Valid @RequestBody CreateCaseDTO createCaseDTO) {
        if (!id.equals(createCaseDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createCaseDTO.id");
        }
        return getUpsertResponse(caseService.upsert(createCaseDTO), createCaseDTO.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCase", summary = "Mark a Case as deleted")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        caseService.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/undelete")
    @Operation(operationId = "undeleteCase", summary = "Revert deletion of a case")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<Void> undeleteCase(@PathVariable UUID id) {
        caseService.undelete(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/close-pending")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2')")
    @Operation(operationId = "closePendingCases", summary = "Close cases in PENDING_CLOSURE state > 29 days")
    public ResponseEntity<Void> closePending() {
        caseService.closePendingCases();
        return ResponseEntity.noContent().build();
    }
}
