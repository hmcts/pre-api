package uk.gov.hmcts.reform.preapi.services.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = {EditRequestPerformService.class})
class EditRequestPerformServiceTest {

    @MockitoBean
    private IEditingService editingService;

    @MockitoBean
    private AssetGenerationService assetGenerationService;

    @MockitoBean
    private EditRequestCrudService editRequestCrudService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private EditRequestDTO mockEditRequestDTO;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private CaptureSession mockCaptureSession;

    @Autowired
    private EditRequestPerformService underTest;

    private final UUID mockEditRequestId = UUID.randomUUID();
    private final UUID mockRecordingId = UUID.randomUUID();
    private final UUID mockParentRecordingId = UUID.randomUUID();
    private final UUID mockCaptureSessionId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws InterruptedException {
        when(mockEditRequest.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequest.getStatus()).thenReturn(EditRequestStatus.PENDING);
        when(mockEditRequest.getSourceRecording()).thenReturn(mockRecording);
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");

        when(mockEditRequestDTO.getId()).thenReturn(mockEditRequestId);
        when(mockEditRequestDTO.getStatus()).thenReturn(EditRequestStatus.PENDING);

        when(mockParentRecording.getId()).thenReturn(mockParentRecordingId);

        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);

        when(mockCaptureSession.getId()).thenReturn(mockCaptureSessionId);

        Integer nextParentVersionNumber = 35;
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(nextParentVersionNumber);

        Integer nextRecordingVersionNumber = 12;
        when(recordingService.getNextVersionNumber(mockRecordingId)).thenReturn(nextRecordingVersionNumber);

        String filename = "filename";
        when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class))).thenReturn(filename);

        when(editRequestCrudService.findById(mockEditRequestId)).thenReturn(mockEditRequestDTO);
    }

    @Test
    @DisplayName("Should attempt to perform edit request and return error on ffmpeg service error")
    void performEditFfmpegError() {
        doThrow(UnknownServerException.class)
            .when(editingService).performEdit(any(UUID.class), eq(mockEditRequest));

        assertThrows(
            Exception.class,
            () -> underTest.performEdit(mockEditRequest)
        );

        ArgumentCaptor<UUID> uuidCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<EditRequestStatus> statusCaptor = ArgumentCaptor.forClass(EditRequestStatus.class);
        verify(editRequestCrudService, times(1)).updateEditRequestStatus(uuidCaptor.capture(),
                                                                         statusCaptor.capture());
        assertThat(uuidCaptor.getValue()).isEqualTo(mockEditRequestId);
        assertThat(statusCaptor.getValue()).isEqualTo(EditRequestStatus.ERROR);

        ArgumentCaptor<EditRequest> performEditCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editingService, times(1)).performEdit(any(UUID.class), performEditCaptor.capture());
        EditRequest performedEditRequest = performEditCaptor.getValue();
        assertThat(performedEditRequest.getId()).isEqualTo(mockEditRequestId);

        verify(recordingService, never()).upsert(any());
    }

    // This should probably be an integration test
    @Test
    @DisplayName("Should perform edit request and return created recording")
    void performEditSuccess() throws InterruptedException {
        when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class))).thenReturn("filename");
        when(recordingService.getNextVersionNumber(mockRecording.getId())).thenReturn(2);

        when(mockEditRequest.getEditInstruction()).thenReturn("{ \"testJson\" :\"edit instructions\"}");

        when(mockEditRequest.getEditInstruction()).thenReturn(
            "{\"requestedInstructions\":null,"
                + "\"ffmpegInstructions\":null,\"forceReencode\":false,"
                + "\"sendNotifications\":true}");


        RecordingDTO newRecordingDto = new RecordingDTO();
        newRecordingDto.setParentRecordingId(mockRecording.getId());
        when(recordingService.findById(any(UUID.class))).thenReturn(newRecordingDto);

        RecordingDTO res = underTest.performEdit(mockEditRequest);
        assertThat(res).isNotNull();
        assertThat(res.getParentRecordingId()).isEqualTo(mockRecording.getId());

        ArgumentCaptor<UUID> newRecIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<EditRequest> saveCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editingService, times(1))
            .performEdit(newRecIdCaptor.capture(), saveCaptor.capture());
        EditRequest updatedEditRequest = saveCaptor.getValue();
        assertThat(updatedEditRequest).isEqualTo(mockEditRequest);

        verify(editRequestCrudService, times(1))
            .updateEditRequestStatus(mockEditRequestId, EditRequestStatus.COMPLETE);

        ArgumentCaptor<CreateRecordingDTO> newRecCaptor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(newRecCaptor.capture());

        assertThat(newRecCaptor.getValue().getId()).isEqualTo(newRecIdCaptor.getValue());
        assertThat(newRecCaptor.getValue().getFilename()).isEqualTo("filename");

        String expectedInstructions = format("""
            {"editRequestId":"%s","editInstructions":{"requestedInstructions":null,
            "ffmpegInstructions":null,"forceReencode":false,"sendNotifications":true}}
            """, mockEditRequestId);
        JSONAssert.assertEquals(newRecCaptor.getValue().getEditInstructions(),
                                expectedInstructions, JSONCompareMode.LENIENT);

        verify(recordingService, times(1)).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        UUID id = UUID.randomUUID();
        doThrow(NotFoundException.class).when(editRequestCrudService).findById(any(UUID.class));

        assertThrows(NotFoundException.class, () -> underTest.markAsProcessing(id));

        verify(editRequestCrudService, times(1)).findById(any(UUID.class));
        verifyNoMoreInteractions(editRequestCrudService);
        verifyNoMoreInteractions(recordingService);
        verifyNoMoreInteractions(editingService);
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PENDING")
    void performEditStatusNotPending() {
        when(mockEditRequestDTO.getStatus()).thenReturn(EditRequestStatus.PROCESSING);

        String message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.markAsProcessing(mockEditRequestId)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + mockEditRequestId
                           + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestCrudService, times(1)).findById(mockEditRequestId);
        verify(editRequestCrudService, never()).upsert(any(), any(), any());
        verify(editingService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        doThrow(PessimisticLockingFailureException.class)
            .when(editRequestCrudService).findById(mockEditRequestId);

        assertThrows(
            PessimisticLockingFailureException.class,
            () -> underTest.markAsProcessing(mockEditRequestId)
        );

        verify(editRequestCrudService, times(1)).findById(mockEditRequestId);
        verify(editRequestCrudService, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("Should return new create recording dto for the edit request")
    void createRecordingSuccess() throws InterruptedException {
        when(mockRecording.getParentRecording()).thenReturn(null);
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");

        String expectedAssetName = "generated_asset_name";
        when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class)))
            .thenReturn(expectedAssetName);

        Integer expectedVersionNumber = 31;
        when(recordingService.getNextVersionNumber(mockRecordingId)).thenReturn(expectedVersionNumber);

        underTest.performEdit(mockEditRequest);

        ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(captor.capture());

        CreateRecordingDTO dto = captor.getValue();

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getParentRecordingId()).isEqualTo(mockRecordingId);
        assertThat(dto.getCaptureSessionId()).isEqualTo(mockCaptureSessionId);
        assertThat(dto.getVersion()).isEqualTo(expectedVersionNumber);
        assertThat(dto.getFilename()).isEqualTo(expectedAssetName);
        assertThat(dto.getDuration()).isEqualTo(null);

        assertThat(dto.getEditInstructions())
            .isEqualTo(format(
                "{\"editRequestId\":\"%s\",\"editInstructions\":{\"requestedInstructions\":null,"
                    + "\"ffmpegInstructions\":null,\"forceReencode\":false,"
                    + "\"sendNotifications\":true}}", mockEditRequestId
            ));

        verify(recordingService, times(1)).getNextVersionNumber(mockRecordingId);
    }

    @Test
    @DisplayName("Should return create recording dto with parent recording")
    void createRecordingDtoWithParentRecording() throws InterruptedException {
        when(mockEditRequest.getEditInstruction()).thenReturn("{}");

        String expectedAssetName = "generated_asset_name";
        when(assetGenerationService.generateAsset(any(UUID.class), any(EditRequest.class)))
            .thenReturn(expectedAssetName);

        Integer wrongVersionNumber = 15;
        when(recordingService.getNextVersionNumber(mockRecordingId)).thenReturn(wrongVersionNumber);

        Integer expectedVersionNumber = 31;
        when(recordingService.getNextVersionNumber(mockParentRecordingId)).thenReturn(expectedVersionNumber);

        underTest.performEdit(mockEditRequest);

        ArgumentCaptor<CreateRecordingDTO> captor = ArgumentCaptor.forClass(CreateRecordingDTO.class);
        verify(recordingService, times(1)).upsert(captor.capture());

        CreateRecordingDTO dto = captor.getValue();

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isNotNull();
        assertThat(dto.getParentRecordingId()).isEqualTo(mockParentRecordingId);
        assertThat(dto.getCaptureSessionId()).isEqualTo(mockCaptureSessionId);
        assertThat(dto.getVersion()).isEqualTo(expectedVersionNumber);
        assertThat(dto.getFilename()).isEqualTo(expectedAssetName);
        assertThat(dto.getDuration()).isEqualTo(null);

        assertThat(dto.getEditInstructions())
            .isEqualTo(format(
                "{\"editRequestId\":\"%s\",\"editInstructions\":{\"requestedInstructions\":null,"
                    + "\"ffmpegInstructions\":null,\"forceReencode\":false,"
                    + "\"sendNotifications\":true}}", mockEditRequestId
            ));
        verify(recordingService, times(1)).getNextVersionNumber(mockParentRecordingId);
    }
}
