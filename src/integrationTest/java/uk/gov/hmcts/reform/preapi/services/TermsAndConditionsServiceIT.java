package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class TermsAndConditionsServiceIT extends IntegrationTestBase {
    @Autowired
    private TermsAndConditionsService termsAndConditionsService;

    private TermsAndConditions termsAndConditionsApp;
    private TermsAndConditions termsAndConditionsPortal;

    @BeforeEach
    public void setUp() {
        termsAndConditionsApp = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "app content");
        entityManager.persist(termsAndConditionsApp);

        termsAndConditionsPortal = HelperFactory.createTermsAndConditions(
            TermsAndConditionsType.PORTAL,
            "portal content"
        );
        entityManager.persist(termsAndConditionsPortal);
    }

    @Test
    @Transactional
    void getLatestTermsAndConditionsApp() {
        var latestAppTerms1 = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP);

        assertThat(latestAppTerms1.getId()).isEqualTo(termsAndConditionsApp.getId());

        var newTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "app content 2");
        entityManager.persist(newTerms);

        var latestAppTerms2 = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP);
        assertThat(latestAppTerms2.getId()).isEqualTo(newTerms.getId());
        assertThat(latestAppTerms2.getCreatedAt()).isAfter(latestAppTerms1.getCreatedAt());
    }

    @Test
    @Transactional
    void getLatestTermsAndConditionsPortal() {
        var latestPortalTerms1 = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);

        assertThat(latestPortalTerms1.getId()).isEqualTo(termsAndConditionsPortal.getId());

        var newTerms = HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "portal content 2");
        entityManager.persist(newTerms);

        var latestPortalTerms2 = termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);
        assertThat(latestPortalTerms2.getId()).isEqualTo(newTerms.getId());
        assertThat(latestPortalTerms2.getCreatedAt()).isAfter(latestPortalTerms1.getCreatedAt());
    }
}
