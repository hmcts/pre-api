package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.List;
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
    public ResponseEntity<List<CaseDTO>> getCases(
        @RequestParam(name = "reference", required = false) String caseReference,
        @RequestParam(name = "courtId", required = false) UUID courtId
    ) {
        return ResponseEntity.ok(caseService.searchBy(caseReference, courtId));
    }

    @PutMapping("/{id}")
    @Operation(operationId = "putCase", summary = "Create or Update a Case")
    public ResponseEntity<Void> upsertCase(@PathVariable UUID id, @RequestBody CreateCaseDTO createCaseDTO) {
        if (createCaseDTO.getId() == null || !id.toString().equals(createCaseDTO.getId().toString())) {
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
