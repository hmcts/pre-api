package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

public class EditCutInstructionDTOTest {
    @Test
    @DisplayName("Should successfully parse start and end times from HH:MM:SS input format")
    void parseTimeSuccess() {
        var startStr = "01:01:01"; // 3661 seconds
        var endStr = "10:10:10"; // 36610 seconds

        var dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        assertThat(dto.getStart()).isEqualTo(3661);
        assertThat(dto.getEnd()).isEqualTo(36610);
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeNumberFormatException() {
        var startStr = "H1:01:01";
        var endStr = "10:10:1S";

        var dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        var message1 = assertThrows(
            BadRequestException.class,
            dto::getStart
        ).getMessage();
        assertThat(message1).isEqualTo("Invalid time format: " + startStr + ". Must be in the form HH:MM:SS");
        var message2 = assertThrows(
            BadRequestException.class,
            dto::getEnd
        ).getMessage();
        assertThat(message2).isEqualTo("Invalid time format: " + endStr + ". Must be in the form HH:MM:SS");
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeIndexOutOfBoundsException() {
        var startStr = "0101:01";
        var endStr = "101010";

        var dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        var message1 = assertThrows(
            BadRequestException.class,
            dto::getStart
        ).getMessage();
        assertThat(message1).isEqualTo("Invalid time format: " + startStr + ". Must be in the form HH:MM:SS");
        var message2 = assertThrows(
            BadRequestException.class,
            dto::getEnd
        ).getMessage();
        assertThat(message2).isEqualTo("Invalid time format: " + endStr + ". Must be in the form HH:MM:SS");
    }
}
