package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditCutInstructions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestFromCsv.class)
public class EditRequestFromCsvTest {

    @MockitoBean
    private User mockUser;

    @MockitoBean
    private EditRequestCrudService editRequestCrudService;

    @Autowired
    private EditRequestFromCsv underTest;

    private static final UUID sourceRecordingId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(mockUser.getId()).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should be able to upsert edit instructions with CSV file")
    void upsertEditInstructionsWithCSVFile() {
        final String fileContents = """
            Edit Number,Start time of cut,End time of cut,Total time removed,Reason
            1,00:00:00,00:00:30,00:30:00,first thirty seconds reason
            2,00:01:01,00:02:00,00:00:59,
            """;

        final List<EditCutInstructionsDTO> expectedInstructions = Stream.of(
            new EditCutInstructions(UUID.randomUUID(), 0, 30, "first thirty seconds reason"),
            new EditCutInstructions(UUID.randomUUID(), 61, 120, "")
        ).map(EditCutInstructionsDTO::new).toList();

        final MockMultipartFile file = new MockMultipartFile(
            "file", "edit_instructions.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        underTest.upsert(sourceRecordingId, file, mockUser);

        ArgumentCaptor<EditRequestDTO> savedEditRequest = ArgumentCaptor.forClass(EditRequestDTO.class);
        verify(editRequestCrudService, times(1))
            .createOrUpsertDraftEditRequestInstructions(savedEditRequest.capture(), mockUser);

        assertThat(savedEditRequest.getValue().getId()).isNotNull();
        assertThat(savedEditRequest.getValue().getSourceRecordingId()).isEqualTo(sourceRecordingId);
        // TODO: Check what the status should be
        assertThat(savedEditRequest.getValue().getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(savedEditRequest.getValue().getCreatedBy()).isEqualTo(mockUser.getId().toString());
        assertThat(savedEditRequest.getValue().getEditCutInstructions()).isEqualTo(expectedInstructions);
    }

    @DisplayName("Should throw an exception if updating edit instructions with non-CSV")
    @Test
    void upsertEditInstructionsWithNotCSVFile() {
        final String fileContents = """
Region,Court,PRE Inbox Address
South East,Example Court,PRE.Edits.Example@justice.gov.uk
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "edits.csv",
            "text/xml", fileContents.getBytes()
        );

        assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(sourceRecordingId, file, mockUser)
        );
    }

    @DisplayName("Should throw an exception if updating edit instructions with empty file")
    @Test
    void upsertEditInstructionsWithEmptyFile() {
        final String fileContents = "";

        MockMultipartFile file = new MockMultipartFile(
            "file", "edits.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(sourceRecordingId, file, mockUser)
        );
    }

}
