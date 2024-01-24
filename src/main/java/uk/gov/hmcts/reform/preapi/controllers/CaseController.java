package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
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
    public HttpEntity<PagedModel<EntityModel<CaseDTO>>> getCases(
        @Parameter(hidden = true) @ModelAttribute SearchCases params,
        @Parameter(hidden = true) Pageable pageable,
        @Parameter(hidden = true) PagedResourcesAssembler<CaseDTO> assembler
    ) {
        var resultPage = caseService.searchBy(params.getReference(), params.getCourtId(), pageable);

        if (pageable.getPageNumber() > resultPage.getTotalPages()) {
            throw new RequestedPageOutOfRangeException(pageable.getPageNumber(), resultPage.getTotalPages());
        }

        return ResponseEntity.ok(assembler.toModel(resultPage));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putCase", summary = "Create or Update a Case")
    public ResponseEntity<Void> upsertCase(@PathVariable UUID id, @Valid @RequestBody CreateCaseDTO createCaseDTO) {
        if (!id.equals(createCaseDTO.getId())) {
            throw new PathPayloadMismatchException("id", "createCaseDTO.id");
        }
        return getUpsertResponse(caseService.upsert(createCaseDTO), createCaseDTO.getId());
    }

    @DeleteMapping("/{id}")
    @Operation(operationId = "deleteCase", summary = "Mark a Case as deleted")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        caseService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
