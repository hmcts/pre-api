package uk.gov.hmcts.reform.preapi.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.services.TermsAndConditionsService;

@RestController
public class TermsAndConditionsController {

    private final TermsAndConditionsService termsAndConditionsService;

    @Autowired
    public TermsAndConditionsController(TermsAndConditionsService termsAndConditionsService) {
        this.termsAndConditionsService = termsAndConditionsService;
    }

    @GetMapping("/api/app-terms-and-conditions/latest")
    public ResponseEntity<TermsAndConditionsDTO> getLatestAppTermsAndConditions() {
        return ResponseEntity.ok(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP));
    }

    @GetMapping("/api/portal-terms-and-conditions/latest")
    public ResponseEntity<TermsAndConditionsDTO> getLatestPortalTermsAndConditions() {
        return ResponseEntity.ok(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL));
    }
}
