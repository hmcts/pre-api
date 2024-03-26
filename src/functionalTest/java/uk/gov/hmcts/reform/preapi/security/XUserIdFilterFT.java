package uk.gov.hmcts.reform.preapi.security;


import io.restassured.response.Response;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

public class XUserIdFilterFT extends FunctionalTestBase {

    private static final String ENDPOINT = "/roles";

    @Test
    void shouldAuthenticateUserExistsSuccess() {
        assertThat(doGetRequest(ENDPOINT, true).getStatusCode()).isNotEqualTo(401);
    }

    @Test
    void shouldNotApplyAuthenticationOnSpecificEndpoints() {
        assertThat(doGetRequest("/swagger-ui/index.html", false).getStatusCode()).isNotEqualTo(401);
        assertThat(doGetRequest("/v3/api-docs", false).getStatusCode()).isNotEqualTo(401);
    }

    @Test
    void shouldFailAuthWhenUserIdNotValid() {
        assertThat(doGetRequest(ENDPOINT, false).getStatusCode()).isEqualTo(401);

        var headersIdEmpty = Map.of(
            X_USER_ID_HEADER, ""
        );
        assertThat(doGetRequest(ENDPOINT, headersIdEmpty, false).getStatusCode()).isEqualTo(401);

        var headersIdNotUserId = Map.of(
            X_USER_ID_HEADER, UUID.randomUUID().toString()
        );
        assertThat(doGetRequest(ENDPOINT, headersIdNotUserId, false).getStatusCode()).isEqualTo(401);

        var headersIdNotUuid = Map.of(
            X_USER_ID_HEADER, "1234567890"
        );
        assertThat(doGetRequest(ENDPOINT, headersIdNotUuid, false).getStatusCode()).isEqualTo(401);

    }
}
