package uk.gov.hmcts.reform.preapi.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;

import java.io.IOException;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class JacksonConfigurationTest {

    @DisplayName("Test Jackson config for timestamp mapping")
    @Test
    void testTimestampMapping() throws JsonProcessingException {
        var mapper = new JacksonConfiguration().getMapper();

        var dto = new CreateCaptureSessionDTO();
        dto.setStartedAt(Timestamp.valueOf("2024-02-29 23:11:59"));
        var result = mapper.writeValueAsString(dto);

        assertThat(result).contains("2024-02-29T23:11:59.000Z");

        var json = "{\"started_at\":\"2024-02-29T23:11:59.000Z\"}";
        var deserialized = mapper.readValue(json, JacksonTestObject.class);

        assertThat(deserialized.getStartedAt().toString()).isEqualTo("2024-02-29 23:11:59.0");

        assertThrows(
            JsonMappingException.class,
            () -> {
                var jsonFail = "{\"started_at\":\"2024-02-29 23:11:59\"}";
                mapper.readValue(jsonFail, JacksonTestObject.class);
            }
        );

        var message = assertThrows(
            IOException.class,
            () -> {
                var jsonFail2 = "{\"started_at\":\"FooBar\"}";
                mapper.readValue(jsonFail2, JacksonTestObject.class);
            }
        ).getMessage();

        assertThat(message).isEqualTo("Unexpected IOException (of type java.io.IOException): Failed to parse Date value 'FooBar'");

    }

    @Data
    @NoArgsConstructor
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    static class JacksonTestObject {

        private Timestamp startedAt;

    }
}
