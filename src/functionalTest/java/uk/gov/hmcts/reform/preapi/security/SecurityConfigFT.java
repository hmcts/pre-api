package uk.gov.hmcts.reform.preapi.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

public class SecurityConfigFT extends FunctionalTestBase {

    private static CreateCaseDTO caseEntity;

    @BeforeEach
    void setUp() {
        caseEntity = createCase();
    }

    @Nested
    @TestPropertySource(properties = "security.enable-csrf=true")
    class WithCsrfEnabled extends FunctionalTestBase {

        @Test
        void shouldBlockEndpointsIfCsrfEnabled() throws JsonProcessingException {
            assertResponseCode(putCase(caseEntity), 403);
        }
    }

    @Nested
    @TestPropertySource(properties = "security.enable-csrf=false")
    class WithCsrfExplicitlyDisabled extends FunctionalTestBase {

        @Test
        void shouldAllowEndpointsIfCsrfExplicitlyDisabled() throws JsonProcessingException {
            assertResponseCode(putCase(caseEntity), 201);
        }
    }

    @Nested
    @TestPropertySource(properties = "security.enable-csrf=")
    class WithCsrfNotSet extends FunctionalTestBase {

        @Test
        void shouldAllowEndpointsByDefaultIfCsrfNotSet() throws JsonProcessingException {
            assertResponseCode(putCase(caseEntity), 201);
        }
    }
}
