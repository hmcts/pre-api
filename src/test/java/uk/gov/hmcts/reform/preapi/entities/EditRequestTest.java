package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestTest {

    @Test
    @DisplayName("Should be able to deserialize from old-style JSON into ordered edit cut requests")
    void deserializeDeprecatedJson() throws IOException {
        var dto = new EditRequest();

        Path resourcePath = Path.of("src/test/resources/edit-requests/existing/sample-for-edit-request-test.json");

        String content = Files.readString(resourcePath, StandardCharsets.UTF_8);

        dto.setEditCutInstructionsFromJson(content);
        assertThat(dto.getEditCutInstructions().size()).isEqualTo(2);

        EditCutInstructions first = dto.getEditCutInstructions().getFirst();
        assertThat(first.getEditRequestId()).isEqualTo(UUID.fromString("f264f9cb-0203-4fa6-9234-b6efec06819e"));
        assertThat(first.getStart()).isEqualTo(180);
        assertThat(first.getEnd()).isEqualTo(300);
        assertThat(first.getReason()).isEqualTo("Removing 2 minutes");

        EditCutInstructions second = dto.getEditCutInstructions().get(1);
        assertThat(second.getEditRequestId()).isEqualTo(UUID.fromString("f264f9cb-0203-4fa6-9234-b6efec06819e"));
        assertThat(second.getStart()).isEqualTo(480);
        assertThat(second.getEnd()).isEqualTo(490);
        assertThat(second.getReason()).isEqualTo("Removing 10 seconds");
    }
}
