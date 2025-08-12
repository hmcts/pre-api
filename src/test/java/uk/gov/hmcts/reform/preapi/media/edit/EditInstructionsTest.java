package uk.gov.hmcts.reform.preapi.media.edit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EditInstructionsTest {
    @Test
    @DisplayName("Should successfully deserialize valid JSON into EditInstructions")
    void fromJson_shouldSuccessfullyDeserializeValidJson() {
        String validJson = """
            {
                "requestedInstructions": [
                    {
                        "start_of_cut": "00:00:00",
                        "end_of_cut": "00:00:10",
                        "start": 0,
                        "end": 10,
                        "reason": "Some reason"
                    }
                ],
                "ffmpegInstructions": [
                    {
                        "start": 10,
                        "end": 20
                    }
                ]
            }
            """;

        EditInstructions result = EditInstructions.fromJson(validJson);

        assertNotNull(result);
        assertThat(result.getRequestedInstructions()).hasSize(1);
        assertThat(result.getFfmpegInstructions()).hasSize(1);

        EditCutInstructionDTO requestedInstruction = result.getRequestedInstructions().getFirst();
        assertThat(requestedInstruction.getStartOfCut()).isEqualTo("00:00:00");
        assertThat(requestedInstruction.getEndOfCut()).isEqualTo("00:00:10");
        assertThat(requestedInstruction.getStart()).isEqualTo(0L);
        assertThat(requestedInstruction.getEnd()).isEqualTo(10L);
        assertThat(requestedInstruction.getReason()).isEqualTo("Some reason");

        FfmpegEditInstructionDTO ffmpegInstruction = result.getFfmpegInstructions().getFirst();
        assertThat(ffmpegInstruction.getStart()).isEqualTo(10L);
        assertThat(ffmpegInstruction.getEnd()).isEqualTo(20L);
    }

    @Test
    @DisplayName("Should throw exception when JSON has invalid structure")
    void fromJson_shouldThrowExceptionForInvalidJsonStructure() {
        String invalidJson = """
            {
                "invalidField": "value"
            }
            """;

        assertUnknownServerExceptionOnParse(invalidJson);
    }

    @Test
    @DisplayName("Should throw exception when JSON is malformed")
    void fromJson_shouldThrowExceptionForMalformedJson() {
        String malformedJson = """
            {
                "requestedInstructions": [
                // syntax error: missing closing bracket
            }
            """;

        assertUnknownServerExceptionOnParse(malformedJson);
    }

    @Test
    @DisplayName("Should throw exception when JSON is null")
    void fromJson_shouldThrowExceptionWhenJsonIsNull() {
        assertUnknownServerExceptionOnParse(null);
    }

    @Test
    @DisplayName("Should throw exception when JSON is empty")
    void fromJson_shouldThrowExceptionWhenJsonIsEmpty() {
        String emptyJson = "";

        assertUnknownServerExceptionOnParse(emptyJson);
    }

    @Test
    @DisplayName("Should return empty lists when JSON has no instructions")
    void fromJson_shouldHandleEmptyJsonObject() {
        String emptyJson2 = "{}";

        EditInstructions result = EditInstructions.fromJson(emptyJson2);

        assertNull(result.getRequestedInstructions());
        assertNull(result.getFfmpegInstructions());
    }

    @Test
    @DisplayName("Should successfully deserialize valid JSON into EditInstructions without error")
    void tryFromJson_shouldSuccessfullyDeserializeValidJson() {
        String validJson = """
            {
                "requestedInstructions": [
                    {
                        "start_of_cut": "00:00:00",
                        "end_of_cut": "00:00:10",
                        "start": 0,
                        "end": 10,
                        "reason": "Some reason"
                    }
                ],
                "ffmpegInstructions": [
                    {
                        "start": 10,
                        "end": 20
                    }
                ]
            }
            """;

        EditInstructions result = EditInstructions.tryFromJson(validJson);

        assertNotNull(result);
    }

    @Test
    @DisplayName("Should return null when encountering a parsing error")
    void tryFromJson_ReturnsNullOnError() {
        assertNull(EditInstructions.tryFromJson("""
            {
                "invalidField": "value"
            }
            """));
        assertNull(EditInstructions.tryFromJson("""
            {
                "requestedInstructions": [
                // syntax error: missing closing bracket
            }
            """));
        assertNull(EditInstructions.tryFromJson(null));
        assertNull(EditInstructions.tryFromJson(""));
    }

    private static void assertUnknownServerExceptionOnParse(String json) {
        String message = assertThrows(
            UnknownServerException.class,
            () -> EditInstructions.fromJson(json)
        ).getMessage();

        assertEquals("Unknown Server Exception: Unable to read edit instructions", message);
    }
}
