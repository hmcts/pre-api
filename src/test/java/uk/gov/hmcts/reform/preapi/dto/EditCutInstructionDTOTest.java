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
        String startStr = "01:01:01"; // 3661 seconds
        String endStr = "10:10:10"; // 36610 seconds

        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        assertThat(dto.getStart()).isEqualTo(3661);
        assertThat(dto.getEnd()).isEqualTo(36610);
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeNumberFormatException() {
        String startStr = "H1:01:01";
        String endStr = "10:10:1S";

        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        String message1 = assertThrows(BadRequestException.class, dto::getStart).getMessage();
        assertThat(message1).isEqualTo("Invalid time format: " + startStr + ". Must be in the form HH:MM:SS");

        String message2 = assertThrows(BadRequestException.class, dto::getEnd).getMessage();
        assertThat(message2).isEqualTo("Invalid time format: " + endStr + ". Must be in the form HH:MM:SS");
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeIndexOutOfBoundsException() {
        String startStr = "0101:01";
        String endStr = "101010";

        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut(startStr)
            .endOfCut(endStr)
            .build();

        String message1 = assertThrows(BadRequestException.class, dto::getStart).getMessage();
        assertThat(message1).isEqualTo("Invalid time format: " + startStr + ". Must be in the form HH:MM:SS");

        String message2 = assertThrows(BadRequestException.class, dto::getEnd).getMessage();
        assertThat(message2).isEqualTo("Invalid time format: " + endStr + ". Must be in the form HH:MM:SS");
    }

    @Test
    @DisplayName("Should return cached start value without parsing again")
    void getStartReturnsCachedValue() {
        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .start(3600L)
            .build();

        assertThat(dto.getStart()).isEqualTo(3600L);
    }

    @Test
    @DisplayName("Should correctly parse and calculate start value when accessed for the first time")
    void getStartParsesStartValue() {
        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut("01:30:00") // 5400 seconds
            .build();

        assertThat(dto.getStart()).isEqualTo(5400L);
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid empty start format")
    void getStartThrowsForEmptyString() {
        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut("")
            .build();

        String message = assertThrows(BadRequestException.class, dto::getStart).getMessage();

        assertThat(message).isEqualTo("Invalid time format: . Must be in the form HH:MM:SS");
    }

    @Test
    @DisplayName("Should throw BadRequestException for null start value")
    void getStartThrowsForNullValue() {
        EditCutInstructionDTO dto = EditCutInstructionDTO.builder()
            .startOfCut(null)
            .build();

        String message = assertThrows(BadRequestException.class, dto::getStart).getMessage();

        assertThat(message).isEqualTo("Invalid time format: null. Must be in the form HH:MM:SS");
    }
}
