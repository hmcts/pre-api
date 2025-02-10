package uk.gov.hmcts.reform.preapi.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BaseFlowRequestInterceptorTest {

    private BaseFlowRequestInterceptor interceptor;
    private RequestTemplate requestTemplate;

    @BeforeEach
    void setUp() {
        interceptor = new BaseFlowRequestInterceptor() {
            @Override
            protected String getFlowKey() {
                return "flow-key";
            }

            protected String getFlowSig() {
                return "flow-sig";
            }

            protected String getFlowId() {
                return "test-flow-id";
            }
        };
        requestTemplate = new RequestTemplate();
    }

    @Test
    void shouldNotModifyRequestWhenFlowIdNotInPath() {
        requestTemplate.uri("/some/other/path");
        interceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).isEmpty();
    }

    @Test
    void shouldModifyRequestWhenFlowIdInPath() {
        requestTemplate.uri("/some/path/test-flow-id");
        interceptor.apply(requestTemplate);

        assertThat(requestTemplate.headers()).isNotEmpty();
        assertThat(requestTemplate.headers().get("Content-Type").stream().findFirst().get())
            .isEqualTo("application/json");
        assertThat(requestTemplate.headers().get("X-Flow-Key").stream().findFirst().get())
            .isEqualTo("flow-key");
        assertThat(requestTemplate.queries().get("api-version").stream().findFirst().get())
            .isEqualTo("2016-06-01");
        assertThat(requestTemplate.queries().get("sp").stream().findFirst().get())
            .isEqualTo("%2Ftriggers%2Fmanual%2Frun");
        assertThat(requestTemplate.queries().get("sv").stream().findFirst().get())
            .isEqualTo("1.0");
        assertThat(requestTemplate.queries().get("sig").stream().findFirst().get())
            .isEqualTo("flow-sig");

    }
}
