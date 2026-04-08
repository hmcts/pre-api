package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditCutInstructionsDTOTest {

    @Test
    @DisplayName("Should set edit request ID from split parameter constructor")
    void setEditRequestIdFromSplitParameterConstructor() {
        UUID editRequestId = UUID.randomUUID();
        String startStr = "01:01:01"; // 3661 seconds
        String endStr = "10:10:10"; // 36610 seconds

        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(editRequestId, startStr, endStr, "reason");

        assertThat(dto.getEditRequestId()).isEqualTo(editRequestId);
    }

    @Test
    @DisplayName("Should set all fields request ID from entity constructor")
    void setEditRequestIdFromEntityConstructor() {
        UUID editRequestId = UUID.randomUUID();

        EditCutInstructions entity = new EditCutInstructions(editRequestId, 10, 20, "reason");
        EditCutInstructionsDTO dto = new  EditCutInstructionsDTO(entity);
        assertThat(dto.getEditRequestId()).isEqualTo(editRequestId);
        assertThat(dto.getStart()).isEqualTo(10);
        assertThat(dto.getEnd()).isEqualTo(20);
        assertThat(dto.getReason()).isEqualTo("reason");
        assertThat(dto.getStartOfCut()).isEqualTo("00:00:10");
        assertThat(dto.getEndOfCut()).isEqualTo("00:00:20");
    }


    @Test
    @DisplayName("Should successfully parse start and end times from HH:MM:SS input string format")
    void parseTimeSuccess() {
        UUID editRequestId = UUID.randomUUID();
        String startStr = "01:01:01"; // 3661 seconds
        String endStr = "10:10:10"; // 36610 seconds

        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(editRequestId, startStr, endStr, "reason");

        assertThat(dto.getStart()).isEqualTo(3661);
        assertThat(dto.getEnd()).isEqualTo(36610);
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start/end time that is not parsable")
    void parseTimeNumberFormatException() {
        try {
            new EditCutInstructionsDTO(UUID.randomUUID(), "H1:01:01", "10:10:1S", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: H1:01:01"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }

    @Test
    @DisplayName("Should throw error when attempting to parse start time that is not parsable")
    void parseTimeIndexOutOfBoundsException() {
        try {
            new EditCutInstructionsDTO(UUID.randomUUID(), "0101:01", "00:22:00", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: 0101:01"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }


    @Test
    @DisplayName("Should correctly parse and calculate start value when accessed for the first time")
    void getStartParsesStartValue() {
        EditCutInstructionsDTO dto = new EditCutInstructionsDTO(
            UUID.randomUUID(),
            "01:30:00", "01:30:00",
            "reason"
        );

        assertThat(dto.getStart()).isEqualTo(5400);
        assertThat(dto.getEnd()).isEqualTo(5400);
        assertThat(dto.getStartOfCut()).isEqualTo("01:30:00");
        assertThat(dto.getEndOfCut()).isEqualTo("01:30:00");
    }

    @Test
    @DisplayName("Should throw BadRequestException for invalid empty start format")
    void getStartThrowsForEmptyString() {
        try {
            new EditCutInstructionsDTO(UUID.randomUUID(), "", "", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: . Must be in the form HH:MM:SS");
        }
    }

    @Test
    @DisplayName("Should throw BadRequestException for null start value")
    void getStartThrowsForNullValue() {
        try {
            new EditCutInstructionsDTO(UUID.randomUUID(), null, "00:22:00", "reason");
        } catch (BadRequestException e) {
            assertThat(e.getMessage()).isEqualTo("Invalid time format: null"
                                                     + ". Must be in the form HH:MM:SS");
        }
    }
}
