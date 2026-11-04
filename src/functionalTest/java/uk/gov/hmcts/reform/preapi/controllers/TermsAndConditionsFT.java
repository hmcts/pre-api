package uk.gov.hmcts.reform.preapi.controllers;

import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class TermsAndConditionsFT extends FunctionalTestBase {

    @Test
    @DisplayName("Should return the latest APP terms when the feature flag is enabled")
    void getLatestAppTerms() {
        // Create an APP terms and conditions record for testing
        Response created = doPostRequest("/testing-support/create-terms-and-conditions/APP",
                                         TestingSupportRoles.SUPER_USER);
        assertResponseCode(created, 200);
        String createdId = created.body().jsonPath().getString("termsId");

        // Get latest APP terms and conditions
        Response response = doGetRequest("/app-terms-and-conditions/latest", TestingSupportRoles.SUPER_USER);
        assertResponseCode(response, 200);
        assertThat(response.body().jsonPath().getString("id")).isEqualTo(createdId);
        assertThat(response.body().jsonPath().getString("type")).isEqualTo("APP");
    }

    @Test
    @DisplayName("Should return the latest Portal terms")
    void getLatestPortalTerms() {
        // Create a Portal terms and conditions record for testing
        Response created = doPostRequest("/testing-support/create-terms-and-conditions/PORTAL",
                                         TestingSupportRoles.SUPER_USER);
        assertResponseCode(created, 200);
        String createdId = created.body().jsonPath().getString("termsId");

        // Get latest Portal terms and conditions
        Response response = doGetRequest("/portal-terms-and-conditions/latest", TestingSupportRoles.SUPER_USER);
        assertResponseCode(response, 200);
        assertThat(response.body().jsonPath().getString("id")).isEqualTo(createdId);
        assertThat(response.body().jsonPath().getString("type")).isEqualTo("PORTAL");
    }
}
