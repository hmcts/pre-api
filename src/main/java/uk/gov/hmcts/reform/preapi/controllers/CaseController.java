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
import uk.gov.hmcts.reform.preapi.cases.services.CaseService;
import uk.gov.hmcts.reform.preapi.models.Case;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController {

    @Autowired
    private CaseService caseService;
    @GetMapping("/{id}")
    public ResponseEntity<Case> getCaseById(@PathVariable(name = "id") UUID caseId) {
        var foundCase = caseService.findById(caseId);

        if (foundCase == null || foundCase.getDeletedAt() != null) {
            // TODO throw not found error
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(foundCase);
    }

    @GetMapping
    public ResponseEntity<List<Case>> getCases(
        @RequestParam(name = "reference", required = false) String caseReference,
        @RequestParam(name = "courtId", required = false) UUID courtId
    ) {
        var foundCases = caseService.searchBy(caseReference, courtId);

        return foundCases.isEmpty()
            ? ResponseEntity.noContent().build()
            : ResponseEntity.ok(foundCases);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Case> createCase(@PathVariable UUID id, @RequestBody Case newCaseRequest) {
        System.out.println(newCaseRequest);
        if (!id.toString().equals(newCaseRequest.getId().toString())) {
            return ResponseEntity.badRequest().build();
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
        return ResponseEntity.noContent().build();
    }
}
