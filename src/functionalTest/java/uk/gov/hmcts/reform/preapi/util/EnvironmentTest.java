package uk.gov.hmcts.reform.preapi.util;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
public class EnvironmentTest extends FunctionalTestBase {

    @Autowired
    private Environment env;

    @Test
    void verifyProfile() {
        String[] activeProfiles = env.getActiveProfiles();
        log.info("Active profiles: {}", Arrays.toString(activeProfiles));
        assertThat(Arrays.asList(activeProfiles)).contains("functionalTest");
    }
}
