package uk.gov.hmcts.reform.preapi.security;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

class XUserIdFilterFT extends FunctionalTestBase {

    private static final String ENDPOINT = "/roles";

    @Test
    void shouldAuthenticateUserExistsSuccess() {
        assertThat(doGetRequest(ENDPOINT, TestingSupportRoles.SUPER_USER).getStatusCode()).isNotEqualTo(401);
    }

    @Test
    void shouldNotApplyAuthenticationOnSpecificEndpoints() {
        assertThat(doGetRequest("/swagger-ui/index.html", null).getStatusCode()).isNotEqualTo(401);
        assertThat(doGetRequest("/v3/api-docs", null).getStatusCode()).isNotEqualTo(401);
    }

    @Test
    void shouldFailAuthWhenUserIdNotValid() {
        assertThat(doGetRequest(ENDPOINT, null).getStatusCode()).isEqualTo(401);

        var headersIdEmpty = Map.of(
            X_USER_ID_HEADER, ""
        );
        assertThat(doGetRequest(ENDPOINT, headersIdEmpty, null).getStatusCode()).isEqualTo(401);

        var headersIdNotUserId = Map.of(
            X_USER_ID_HEADER, UUID.randomUUID().toString()
        );
        assertThat(doGetRequest(ENDPOINT, headersIdNotUserId, null).getStatusCode()).isEqualTo(401);

        var headersIdNotUuid = Map.of(
            X_USER_ID_HEADER, "1234567890"
        );
        assertThat(doGetRequest(ENDPOINT, headersIdNotUuid, null).getStatusCode()).isEqualTo(401);

    }
}
