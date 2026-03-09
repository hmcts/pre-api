package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;

public class EditCutInstructionsDTOTest {
    @Test
    @DisplayName("Should successfully parse start and end times from HH:MM:SS input string format")
    void parseTimeSuccess() {
        String startStr = "01:01:01"; // 3661 seconds
        String endStr = "10:10:10"; // 36610 seconds

        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(startStr, endStr, "reason");

        assertThat(dto.getStart()).isEqualTo(3661);
        assertThat(dto.getEnd()).isEqualTo(36610);
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeNumberFormatException() {
        try {
            new EditCutInstructionsDTO("H1:01:01", "10:10:1S", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: H1:01:01"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start time that is not parsable")
    void parseTimeIndexOutOfBoundsException() {
        try {
            new EditCutInstructionsDTO("0101:01", "00:22:00", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: 0101:01"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }


    @Test
    @DisplayName("Should correctly parse and calculate start value when accessed for the first time")
    void getStartParsesStartValue() {
        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(
            "01:30:00", "01:30:00",
            "reason"
        );

        assertThat(dto.getStart()).isEqualTo(5400L);
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid empty start format")
    void getStartThrowsForEmptyString() {
        try {
            new EditCutInstructionsDTO("", "", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: . Must be in the form HH:MM:SS");
        }
    }

    @Test
    @DisplayName("Should throw BadRequestException for null start value")
    void getStartThrowsForNullValue() {
        try {
            new EditCutInstructionsDTO(null, "00:22:00", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: null"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }
}
