package uk.gov.hmcts.reform.preapi.openapi;

import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import uk.gov.hmcts.reform.preapi.config.ContainerImageNameSubstitutor;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Built-in feature which saves service's swagger specs in temporary directory.
 * Each CI run on master should automatically save and upload (if updated) documentation.
 */
@Slf4j
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
class OpenAPIPublisher {

    @Autowired
    private MockMvc mvc;

    @Value("${api.name}")
    private String apiName;

    @DisplayName("Generate swagger documentation")
    @Test
    void generateDocs() throws Exception {
        Assertions.assertThat(mvc).isNotNull();
        Assertions.assertThat(postgresContainer.isRunning()).isTrue();

        System.out.println("API NAME: " + apiName);
        Assertions.assertThat(apiName).isNotBlank();
        Assertions.assertThat(apiName).isEqualTo("pre-api-b2c");

        byte[] specs = mvc.perform(get("/v3/api-docs/" + apiName))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        Assertions.assertThat(specs).isNotEmpty();
        log.info("Swagger documentation generated for API " + apiName);

        try (OutputStream outputStream = Files.newOutputStream(Paths.get("/tmp/openapi-specs.json"))) {
            outputStream.write(specs);
        }

        byte[] writtenBytes;
        try (InputStream inputStream = Files.newInputStream(Paths.get("/tmp/openapi-specs.json"))) {
            writtenBytes = inputStream.readAllBytes();
        }
        Assertions.assertThat(writtenBytes).isNotEmpty();
        Assertions.assertThat(specs).isEqualTo(writtenBytes);
    }

    static PostgreSQLContainer<?> postgresContainer = new PostgreSQLContainer<>(
        ContainerImageNameSubstitutor.instance().apply(DockerImageName.parse("postgres"))
    );

    @BeforeAll
    static void beforeAll() {
        postgresContainer.start();
    }

    @AfterAll
    static void afterAll() {
        postgresContainer.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }
}
