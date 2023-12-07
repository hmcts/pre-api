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
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.exception.PathPayloadMismatchException;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/cases")
public class CaseController extends PreApiController<CaseDTO> {

    private final CaseService caseService;

    @Autowired
    public CaseController(CaseService caseService) {
        this.caseService = caseService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaseDTO> getCaseById(@PathVariable(name = "id") UUID caseId) {
        return ResponseEntity.ok(caseService.findById(caseId));
    }

    @GetMapping
    public ResponseEntity<List<CaseDTO>> getCases(
        @RequestParam(name = "reference", required = false) String caseReference,
        @RequestParam(name = "courtId", required = false) UUID courtId
    ) {
        return ResponseEntity.ok(caseService.searchBy(caseReference, courtId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CaseDTO> upsertCase(@PathVariable UUID id, @RequestBody CaseDTO caseDTO) {
        if (!id.toString().equals(caseDTO.getId().toString())) {
            throw new PathPayloadMismatchException("id", "caseDTO.id");
        }

        return getUpsertResponse(caseService.upsert(caseDTO), caseDTO.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCase(@PathVariable UUID id) {
        caseService.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
