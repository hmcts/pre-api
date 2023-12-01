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
import uk.gov.hmcts.reform.preapi.cases.models.PutCaseRequest;
import uk.gov.hmcts.reform.preapi.cases.services.CaseService;
import uk.gov.hmcts.reform.preapi.courts.services.CourtService;
import uk.gov.hmcts.reform.preapi.models.Case;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/cases")
public class CaseController {

    @Autowired
    private CaseService caseService;

    @Autowired
    private CourtService courtService;

    @GetMapping("/{id}")
    public ResponseEntity<Case> getCaseById(@PathVariable(name = "id") UUID caseId) {
        var foundCase = caseService.findById(caseId);

        if (foundCase == null || foundCase.isDeleted()) {
            // TODO throw not found error
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new Case(foundCase));
    }

    @GetMapping
    public ResponseEntity<List<Case>> getCases(
        @RequestParam(name = "reference", required = false) String caseReference,
        @RequestParam(name = "courtId", required = false) UUID courtId
    ) {
        var foundCases = caseService.searchBy(caseReference, courtId);

        return foundCases.isEmpty()
            ? ResponseEntity.noContent().build()
            : ResponseEntity.ok(foundCases
                                    .stream()
                                    .map(Case::new)
                                    .collect(Collectors.toList())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Case> updateCase(@PathVariable UUID id, @RequestBody PutCaseRequest updatedCase) {
        var existingCase = caseService.findById(id);

        if (existingCase == null || existingCase.isDeleted()) {
            // todo throw not found
            return ResponseEntity.notFound().build();
        }

        if (updatedCase.getCourtId() != null) {
            var court = courtService.findById(updatedCase.getCourtId());
            if (court == null) {
                // todo throw bad request
                return ResponseEntity.badRequest().build();
            }
            existingCase.setCourt(court);
        }

        if (updatedCase.getReference() != null) {
            existingCase.setReference(updatedCase.getReference());
        }

        if (updatedCase.getTest() != null) {
            existingCase.setTest(updatedCase.getTest());
        }

        return ResponseEntity.ok(new Case(caseService.save(existingCase)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        var existingCase = caseService.findById(id);

        if (existingCase == null || existingCase.isDeleted()) {
            // todo throw not found
            return ResponseEntity.notFound().build();
        }

        caseService.delete(existingCase);
        return ResponseEntity.noContent().build();
    }
}
