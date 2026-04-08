package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestDTOTest {
    private static EditRequest editRequest;

    @BeforeAll
    static void setUp() {
        editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setEditCutInstructions(List.of());
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setFinishedAt(Timestamp.from(Instant.now()));
        editRequest.setCreatedAt(Timestamp.from(Instant.now()));
        editRequest.setModifiedAt(Timestamp.from(Instant.now()));

        var user =  new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);
        editRequest.setSourceRecordingId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should create an edit request dto from edit request entity")
    void testConstructorFromEntity() {
        EditCutInstructions firstEditInstructions = new EditCutInstructions(editRequest.getId(), 300, 500, "first edit");
        EditCutInstructions secondEditInstructions = new EditCutInstructions(editRequest.getId(), 600, 750, "second edit");
        editRequest.setEditCutInstructions(List.of(firstEditInstructions, secondEditInstructions));

        var dto = new EditRequestDTO(editRequest);
        assertThat(dto.getId()).isEqualTo(editRequest.getId());
        assertThat(dto.getEditCutInstructions().size()).isEqualTo(2);

        EditCutInstructionsDTO first = dto.getEditCutInstructions().getFirst();
        assertThat(first.getEditRequestId()).isEqualTo(editRequest.getId());
        assertThat(first.getReason()).isEqualTo(firstEditInstructions.getReason());
        assertThat(first.getStart()).isEqualTo(firstEditInstructions.getStart());
        assertThat(first.getEnd()).isEqualTo(firstEditInstructions.getEnd());

        EditCutInstructionsDTO second = dto.getEditCutInstructions().getLast();
        assertThat(second.getEditRequestId()).isEqualTo(editRequest.getId());
        assertThat(second.getReason()).isEqualTo(secondEditInstructions.getReason());
        assertThat(second.getStart()).isEqualTo(secondEditInstructions.getStart());
        assertThat(second.getEnd()).isEqualTo(secondEditInstructions.getEnd());

        assertThat(dto.getCreatedAt()).isEqualTo(editRequest.getCreatedAt());
        assertThat(dto.getStatus()).isEqualTo(editRequest.getStatus());
        assertThat(dto.getStartedAt()).isEqualTo(editRequest.getStartedAt());
        assertThat(dto.getFinishedAt()).isEqualTo(editRequest.getFinishedAt());
        assertThat(dto.getCreatedAt()).isEqualTo(editRequest.getCreatedAt());
        assertThat(dto.getModifiedAt()).isEqualTo(editRequest.getModifiedAt());
        assertThat(dto.getCreatedById()).isEqualTo(editRequest.getCreatedBy().getId());
        assertThat(dto.getSourceRecordingId()).isEqualTo(editRequest.getSourceRecordingId());
    }

    @Test
    @DisplayName("Should create an edit request dto with empty edit instructions")
    void testConstructorFromEntityEmptyEditInstructions() {
        editRequest.setEditCutInstructions(List.of());
        var dto = new EditRequestDTO(editRequest);

        assertThat(dto.getId()).isEqualTo(editRequest.getId());
        assertThat(dto.getEditCutInstructions()).isEmpty();
        assertThat(dto.getStatus()).isEqualTo(editRequest.getStatus());
        assertThat(dto.getStartedAt()).isEqualTo(editRequest.getStartedAt());
        assertThat(dto.getFinishedAt()).isEqualTo(editRequest.getFinishedAt());
        assertThat(dto.getCreatedAt()).isEqualTo(editRequest.getCreatedAt());
        assertThat(dto.getModifiedAt()).isEqualTo(editRequest.getModifiedAt());
        assertThat(dto.getCreatedById()).isEqualTo(editRequest.getCreatedBy().getId());
        assertThat(dto.getSourceRecordingId()).isEqualTo(editRequest.getSourceRecordingId());
    }

    @Test
    @DisplayName("Should convert list of instructions to and from DTO")
    void testConvertListOfInstructionsToAndFromDTO() {
        EditCutInstructions firstEditInstructions = new EditCutInstructions(editRequest.getId(), 300, 500, "first edit");
        EditCutInstructions secondEditInstructions = new EditCutInstructions(editRequest.getId(), 600, 750, "second edit");
        List<EditCutInstructions> nonDto = List.of(firstEditInstructions, secondEditInstructions);

        List<EditCutInstructionsDTO> dtoList = EditRequestDTO.toDTO(nonDto);

        assertThat(dtoList.size()).isEqualTo(nonDto.size());

        assertThat(dtoList.getFirst().getReason()).isEqualTo(nonDto.getFirst().getReason());
        assertThat(dtoList.getFirst().getStart()).isEqualTo(nonDto.getFirst().getStart());
        assertThat(dtoList.getFirst().getEnd()).isEqualTo(nonDto.getFirst().getEnd());

        assertThat(dtoList.get(1).getReason()).isEqualTo(nonDto.get(1).getReason());
        assertThat(dtoList.get(1).getStart()).isEqualTo(nonDto.get(1).getStart());
        assertThat(dtoList.get(1).getEnd()).isEqualTo(nonDto.get(1).getEnd());

        List<EditCutInstructions> convertedBackFromDto = EditRequest.fromDTO(dtoList);

        assertThat(convertedBackFromDto.getFirst().getEditRequestId()).isEqualTo(nonDto.getFirst().getEditRequestId());
        assertThat(convertedBackFromDto.getFirst().getReason()).isEqualTo(nonDto.getFirst().getReason());
        assertThat(convertedBackFromDto.getFirst().getStart()).isEqualTo(nonDto.getFirst().getStart());
        assertThat(convertedBackFromDto.getFirst().getEnd()).isEqualTo(nonDto.getFirst().getEnd());

        assertThat(convertedBackFromDto.getLast().getEditRequestId()).isEqualTo(nonDto.getLast().getEditRequestId());
        assertThat(convertedBackFromDto.getLast().getReason()).isEqualTo(nonDto.getLast().getReason());
        assertThat(convertedBackFromDto.getLast().getStart()).isEqualTo(nonDto.getLast().getStart());
        assertThat(convertedBackFromDto.getLast().getEnd()).isEqualTo(nonDto.getLast().getEnd());
    }

}

