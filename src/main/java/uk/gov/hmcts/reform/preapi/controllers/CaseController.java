package uk.gov.hmcts.reform.preapi.controllers;

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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.model.Case;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController {

    private final CaseService caseService;

    @Autowired
    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Case> getCaseById(@PathVariable(name = "id") UUID caseId) {
        var foundCase = caseService.findById(caseId);

        if (foundCase == null || foundCase.getDeletedAt() != null) {
            throw new NotFoundException("Case: " + caseId);
        }
        return ResponseEntity.ok(foundCase);
    }

    @GetMapping
    public ResponseEntity<List<Case>> getCases(
        @RequestParam(name = "reference", required = false) String caseReference,
        @RequestParam(name = "courtId", required = false) UUID courtId
    ) {
        return ResponseEntity.ok(caseService.searchBy(caseReference, courtId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Case> createCase(@PathVariable UUID id, @RequestBody Case newCaseRequest) {
        if (!id.toString().equals(newCaseRequest.getId().toString())) {
            throw new PathPayloadMismatchException("id", "newCaseRequest.id");
        }

        caseService.create(newCaseRequest);

        return ResponseEntity.created(
            ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("")
                .buildAndExpand(id)
                .toUri())
            .build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        caseService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
