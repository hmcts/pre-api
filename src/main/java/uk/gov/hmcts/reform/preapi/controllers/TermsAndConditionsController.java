package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.services.TermsAndConditionsService;
import uk.gov.hmcts.reform.preapi.services.UserTermsAcceptedService;

import java.util.UUID;

@RestController
public class TermsAndConditionsController {

    private final TermsAndConditionsService termsAndConditionsService;
    private final UserTermsAcceptedService userTermsAcceptedService;

    @Autowired
    public TermsAndConditionsController(TermsAndConditionsService termsAndConditionsService,
                                        UserTermsAcceptedService userTermsAcceptedService) {
        this.termsAndConditionsService = termsAndConditionsService;
        this.userTermsAcceptedService = userTermsAcceptedService;
    }

    @GetMapping("/api/app-terms-and-conditions/latest")
    @Operation(
        operationId = "getLatestTermsForApp",
        summary = "Get the latest terms and conditions for the app"
    )
    public ResponseEntity<TermsAndConditionsDTO> getLatestAppTermsAndConditions() {
        return ResponseEntity.ok(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP));
    }

    @GetMapping("/api/portal-terms-and-conditions/latest")
    @Operation(
        operationId = "getLatestTermsForPortal",
        summary = "Get the latest terms and conditions for the portal"
    )
    public ResponseEntity<TermsAndConditionsDTO> getLatestPortalTermsAndConditions() {
        return ResponseEntity.ok(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL));
    }

    @PostMapping("/accept-terms-and-conditions/{termsId}")
    @Operation(
        operationId = "acceptTermsAndConditions",
        summary = "Accept terms and conditions for a user"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<Void> acceptTermsAndConditions(@PathVariable UUID termsId) {
        userTermsAcceptedService.acceptTermsAndConditions(termsId);
        return ResponseEntity.noContent().build();
    }
}
