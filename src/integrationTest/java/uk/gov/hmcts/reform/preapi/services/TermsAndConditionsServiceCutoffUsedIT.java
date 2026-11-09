package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@TestPropertySource(properties = {
    "feature-flags.dynatrace-terms-and-conditions.enabled=false",
    "feature-flags.dynatrace-terms-and-conditions.cut-off-date=2026-07-15"
})
public class TermsAndConditionsServiceCutoffUsedIT extends IntegrationTestBase {

    @Autowired
    private TermsAndConditionsService termsAndConditionsService;

    @Test
    @Transactional
    public void getLatestAppTermsAndConditionsCreatedBeforeCutoff() {
        //Create an app terms that is before cutoff date
        TermsAndConditions termsCreatedBeforeCutoff =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "<h1>Terms and Conditions</h1>");
        entityManager.persist(termsCreatedBeforeCutoff);
        termsCreatedBeforeCutoff
            .setCreatedAt(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 14).atStartOfDay()));
        entityManager.flush();
        entityManager.clear();

        //Create an app terms that is after cutoff date
        TermsAndConditions termsCreatedAfterCutoff =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "<h1>Terms and Conditions</h1>");
        entityManager.persist(termsCreatedAfterCutoff);
        termsCreatedAfterCutoff
            .setCreatedAt(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 16).atStartOfDay()));
        entityManager.flush();
        entityManager.clear();

        TermsAndConditionsDTO result =
            termsAndConditionsService.getLatestTermsAndConditionsByType(TermsAndConditionsType.APP);

        assertThat(result.getId()).isEqualTo(termsCreatedBeforeCutoff.getId());
        assertThat(result.getCreatedAt())
            .isBefore(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 15).atStartOfDay()));
    }

    @Test
    @Transactional
    public void getLatestPortalTermsAndConditionsIgnoresCutoff() {

        //Create a portal terms that is before cutoff date
        TermsAndConditions termsCreatedBeforeCutoff =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "# Terms and Conditions");
        entityManager.persist(termsCreatedBeforeCutoff);
        termsCreatedBeforeCutoff
            .setCreatedAt(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 14).atStartOfDay()));
        entityManager.flush();
        entityManager.clear();

        //Create a portal terms that is after cutoff date
        TermsAndConditions termsCreatedAfterCutoff =
            HelperFactory.createTermsAndConditions(TermsAndConditionsType.PORTAL, "# Terms and Conditions");
        entityManager.persist(termsCreatedAfterCutoff);
        termsCreatedAfterCutoff
            .setCreatedAt(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 16).atStartOfDay()));
        entityManager.flush();
        entityManager.clear();

        TermsAndConditionsDTO result
            = termsAndConditionsService.getLatestTermsAndConditionsByType(TermsAndConditionsType.PORTAL);

        assertThat(result.getCreatedAt())
            .isAfter(java.sql.Timestamp.valueOf(LocalDate.of(2026, 7, 15).atStartOfDay()));
    }
}
