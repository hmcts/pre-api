package uk.gov.hmcts.reform.preapi.cases.controllers;

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
import uk.gov.hmcts.reform.preapi.cases.models.PutCaseRequestModel;
import uk.gov.hmcts.reform.preapi.cases.services.CaseService;
import uk.gov.hmcts.reform.preapi.courts.services.CourtService;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.models.CaseDto;

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
    public ResponseEntity<CaseDto> getCaseById(@PathVariable(name = "id") UUID caseId) {
        Case foundCase = caseService.findById(caseId);
        if (foundCase == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(new CaseDto(foundCase));
    }

    @GetMapping
    public ResponseEntity<List<CaseDto>> getCases(@RequestParam(name = "reference", required = false) String caseReference) {
        List<Case> foundCases;
        if (caseReference != null && !caseReference.isEmpty()) {
            foundCases = caseService.findByReference(caseReference);
        } else {
            foundCases = caseService.findAll();
        }

        return foundCases.isEmpty()
            ? ResponseEntity.noContent().build()
            : ResponseEntity.ok(foundCases
                                    .stream()
                                    .map(CaseDto::new)
                                    .collect(Collectors.toList())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<CaseDto> updateCase(@PathVariable UUID id, @RequestBody PutCaseRequestModel updatedCase) {
        Case existingCase = caseService.findById(id);

        if (existingCase == null) {
            return ResponseEntity.notFound().build();
        }

        if (updatedCase.getCourtId() != null) {
            var court = courtService.findById(updatedCase.getCourtId());
            if (court == null) {
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

        CaseDto savedCase = new CaseDto(caseService.save(existingCase));

        return ResponseEntity.ok(savedCase);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        Case existingCase = caseService.findById(id);

        if (existingCase == null) {
            return ResponseEntity.notFound().build();
        }

        caseService.delete(existingCase);

        return ResponseEntity.noContent().build();
    }
}
