package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = EditRequestService.class)
public class EditRequestServiceTest {
    @MockitoBean
    private EditRequestRepository editRequestRepository;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private FfmpegService ffmpegService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private IMediaService mediaService;

    @MockitoBean
    private EditNotificationService editNotificationService;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    @MockitoBean
    private UserAuthentication mockAuth;

    @MockitoBean
    private AppAccess mockAppAccess;

    @MockitoBean
    private CaptureSession mockCaptureSession;

    @MockitoBean
    private EditRequest mockEditRequest;

    @MockitoBean
    private CaptureSession captureSession;

    private Recording recording;

    @Autowired
    private EditRequestService underTest;

    private User shareWith1;
    private User shareWith2;
    private User courtClerkUser;
    private Case testCase;
    private Court court;
    private Booking booking;
    private ShareBooking shareBooking1;
    private ShareBooking shareBooking2;

    private static UUID mockRecordingId = UUID.randomUUID();
    private static UUID mockParentRecId = UUID.randomUUID();
    private static UUID mockCaptureSessionId = UUID.randomUUID();

    @BeforeEach
    void setup() {

        shareWith1 = HelperFactory.createUser("First", "User", "example1@example.com",
                                              new Timestamp(System.currentTimeMillis()), null, null);

        shareWith2 = HelperFactory.createUser("Second", "User", "example2@example.com",
                                              new Timestamp(System.currentTimeMillis()), null, null);

        courtClerkUser = HelperFactory.createUser("Court", "Clerk", "court.clerk@example.com",
                                                  new Timestamp(System.currentTimeMillis()), null, null);

        court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC");

        testCase = HelperFactory.createCase(court, "Test Case", false, null);

        booking = HelperFactory.createBooking(testCase, new Timestamp(System.currentTimeMillis()), null);

        shareBooking1 = HelperFactory.createShareBooking(shareWith1, courtClerkUser, booking,
                                                         new Timestamp(System.currentTimeMillis()));

        shareBooking2 = HelperFactory.createShareBooking(shareWith2, courtClerkUser, booking,
                                                         new Timestamp(System.currentTimeMillis()));

        booking.setShares(Set.of(shareBooking1, shareBooking2));

        mockAppAccess.setUser(courtClerkUser);

        when(mockAuth.getAppAccess()).thenReturn(mockAppAccess);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(mockCaptureSession.getBooking()).thenReturn(booking);
        when(mockCaptureSession.getId()).thenReturn(mockCaptureSessionId);

        when(mockRecording.getId()).thenReturn(mockRecordingId);
        when(mockRecording.getCaptureSession()).thenReturn(mockCaptureSession);
        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));
        when(mockRecording.getFilename()).thenReturn("filename");

        when(mockParentRecording.getId()).thenReturn(mockParentRecId);
        when(mockParentRecording.getCaptureSession()).thenReturn(mockCaptureSession);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);

        when(azureFinalStorageService.getRecordingDuration(mockRecordingId)).thenReturn(Duration.ofMinutes(3));
        when(azureFinalStorageService.getMp4FileName(mockRecordingId.toString())).thenReturn("filename");
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId)).thenReturn(Optional.of(recording));
        when(mockEditRequest.getId()).thenReturn(UUID.randomUUID());
    }

    @Test
    @DisplayName("Should return all pending edit requests")
    void getPendingEditRequestsSuccess() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        when(editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING))
            .thenReturn(Optional.of(editRequest));

        var res = underTest.getNextPendingEditRequest();

        assertThat(res).isPresent();
        assertThat(res.get().getId()).isEqualTo(editRequest.getId());
        assertThat(res.get().getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should attempt to perform edit request and return error on ffmpeg service error")
    void performEditFfmpegError() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setSourceRecording(mockRecording);

        when(editRequestRepository.findById(any())).thenReturn(Optional.of(editRequest));

        doThrow(UnknownServerException.class)
            .when(ffmpegService).performEdit(any(UUID.class), eq(editRequest));

        assertThrows(
            Exception.class,
            () -> underTest.performEdit(editRequest)
        );

        verify(editRequestRepository, times(1)).findById(editRequest.getId());

        ArgumentCaptor<EditRequest> saveCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(saveCaptor.capture());
        EditRequest updatedEditRequest = saveCaptor.getValue();
        assertThat(updatedEditRequest.getId()).isEqualTo(editRequest.getId());
        assertThat(updatedEditRequest.getStatus()).isEqualTo(EditRequestStatus.ERROR);

        ArgumentCaptor<EditRequest> performEditCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), performEditCaptor.capture());
        EditRequest performedEditRequest = performEditCaptor.getValue();
        assertThat(performedEditRequest.getId()).isEqualTo(editRequest.getId());

        verify(recordingService, never()).upsert(any());
    }

    @Test
    @DisplayName("Should perform edit request and return created recording")
    void performEditSuccess() throws InterruptedException {
        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setSourceRecording(mockRecording);
        editRequest.setEditInstruction("{}");

        when(editRequestRepository.findById(any())).thenReturn(Optional.of(editRequest));
        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));
        when(recordingService.getNextVersionNumber(mockRecording.getId())).thenReturn(2);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);

        var newRecordingDto = new RecordingDTO();
        newRecordingDto.setParentRecordingId(mockRecording.getId());
        when(recordingService.findById(any(UUID.class))).thenReturn(newRecordingDto);
        when(azureIngestStorageService.doesContainerExist(anyString())).thenReturn(true);
        var importResponse = new GenerateAssetResponseDTO();
        importResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(importResponse);
        when(azureFinalStorageService.getMp4FileName(anyString())).thenReturn("index.mp4");

        var res = underTest.performEdit(editRequest);
        assertThat(res).isNotNull();
        assertThat(res.getParentRecordingId()).isEqualTo(mockRecording.getId());

        verify(editRequestRepository, times(1)).findById(editRequest.getId());

        ArgumentCaptor<EditRequest> saveCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(saveCaptor.capture());
        EditRequest updatedEditRequest = saveCaptor.getValue();
        assertThat(updatedEditRequest.getId()).isEqualTo(editRequest.getId());
        assertThat(updatedEditRequest.getStatus()).isEqualTo(EditRequestStatus.COMPLETE);

        ArgumentCaptor<EditRequest> performEditCaptor = ArgumentCaptor.forClass(EditRequest.class);
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), performEditCaptor.capture());
        EditRequest performedEditRequest = performEditCaptor.getValue();
        assertThat(performedEditRequest.getId()).isEqualTo(editRequest.getId());

        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, times(1)).findById(any(UUID.class));
        verify(azureIngestStorageService, times(1)).doesContainerExist(anyString());
        verify(azureIngestStorageService, times(1)).getMp4FileName(anyString());
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(anyString());

        // Notification is sent by RecordingListener instead
        verify(editNotificationService, times(0)).sendNotifications(booking);
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        var id = UUID.randomUUID();

        when(editRequestRepository.findById(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.markAsProcessing(id)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findById(id);
        verify(editRequestRepository, never()).save(any(EditRequest.class));
        verify(editRequestRepository, never()).saveAndFlush(any(EditRequest.class));
        verify(ffmpegService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PENDING")
    void performEditStatusNotPending() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PROCESSING);

        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.markAsProcessing(editRequest.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + editRequest.getId()
                           + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).save(any(EditRequest.class));
        verify(ffmpegService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        doThrow(PessimisticLockingFailureException.class)
            .when(editRequestRepository).findById(editRequest.getId());

        assertThrows(
            PessimisticLockingFailureException.class,
            () -> underTest.markAsProcessing(editRequest.getId())
        );

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).saveAndFlush(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should create a new edit request")
    void createEditRequestSuccess() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecording.getId());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecording.getId()))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        var response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.CREATED);

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(mockRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should update an edit request")
    void updateEditRequestSuccess() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecording.getId());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecording.getId()))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        assertThat(editRequest.getId()).isEqualTo(dto.getId());
        assertThat(editRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(mockRecording.getId());
        assertThat(editRequest.getEditInstruction())
            .contains("\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]");

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(mockRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw not found when source recording does not exist")
    void createEditRequestSourceRecordingNotFound() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);

        when(recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(dto)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Recording: " + dto.getSourceRecordingId());

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(dto.getSourceRecordingId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(dto.getSourceRecordingId());
        verify(editRequestRepository, never()).findById(any());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a duration")
    void createEditRequestDurationIsNullError() {
        when(mockRecording.getDuration()).thenReturn(null);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecordingId);
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> underTest.upsert(dto)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Source Recording (" + mockRecordingId + ") does not have a valid duration");

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(mockRecordingId);
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(editRequestRepository, never()).findById(any());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, never()).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw bad request when instruction cuts entire recording")
    void invertInstructionsBadRequestCutToZeroDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(0L)
                             .end(180L)
                             .build());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot cut an entire recording: "
                           + "Start(00:00:00), End(00:03:00), "
                           + "Recording Duration(00:03:00)");
    }

    @Test
    @DisplayName("Should delete edit request when upserting with empty instructions")
    void deleteEmptyInstructions() {
        UUID sourceRecordingId = UUID.randomUUID();
        Recording sourceRecording = new Recording();
        sourceRecording.setId(sourceRecordingId);
        sourceRecording.setDuration(Duration.ofSeconds(30));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(sourceRecordingId);
        request.setEditInstructions(new ArrayList<>());

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecordingId))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(request.getId())).thenReturn(Optional.of(new EditRequest()));

        UpsertResult result = editRequestService.upsert(request);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);
    }

    @Test
    @DisplayName("Should ignore attempt to delete non-existent edit request")
    void deleteNonExistentEditRequestSuccess() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);

        when(recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId()))
            .thenReturn(Optional.of(recording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        editRequestService.delete(dto);

        verify(editRequestRepository, times(0)).delete(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw bad request when trying to create new edit request with empty instructions")
    void badRequestEmptyInstructions() {

        UUID sourceRecordingId = UUID.randomUUID();
        var sourceRecording = new Recording();
        sourceRecording.setId(sourceRecordingId);
        sourceRecording.setDuration(Duration.ofSeconds(30));

        CreateEditRequestDTO originalRequest = new CreateEditRequestDTO();
        originalRequest.setId(UUID.randomUUID());
        originalRequest.setSourceRecordingId(sourceRecordingId);
        originalRequest.setEditInstructions(new ArrayList<>());

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecordingId))
            .thenReturn(Optional.of(sourceRecording));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.upsert(originalRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot create an edit request with empty instructions");
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void invertInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(60L)
                             .build());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: "
                           + "Start(00:01:00), End(00:01:00)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void invertInstructionsBadRequestEndLTStart() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(50L)
                             .build());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with end time before start time: "
                           + "Start(00:01:00), End(00:00:50)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void invertInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(200L) // duration is 180
                             .build());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction end time exceeding duration: "
                           + "Start(00:01:00), End(00:03:20), "
                           + "Recording Duration(00:03:00)");
    }

    @Test
    @DisplayName("Should throw bad request when instructions overlap")
    void invertInstructionsOverlap() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(10L)
                             .end(30L)
                             .build());
        instructions.add(EditCutInstructionDTO.builder()
                             .start(20L)
                             .end(40L)
                             .build());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message).isEqualTo("Overlapping instructions: "
                                          + "Previous End(00:00:30), Current Start(00:00:20)");
    }

    @Test
    @DisplayName("Should return inverted instructions (ordered correctly)")
    void invertInstructionsSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(120L)
                              .build());
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(150L)
                              .end(180L)
                              .build());

        var expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(120)
                .end(150)
                .build()
        );

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 underTest.invertInstructions(instructions1, mockRecording));
    }

    @Test
    @DisplayName("Should return inverted instructions (ordered correctly) when not cutting the end")
    void invertInstructionsNotCuttingEndSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(120L)
                              .build());
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(150L)
                              .end(160L)
                              .build());

        var expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(120)
                .end(150)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(160)
                .end(180)
                .build()
        );

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 underTest.invertInstructions(instructions1, mockRecording));
    }

    @Test
    @DisplayName("Should return edit request when it exists")
    void findByIdSuccess() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(mockRecording);
        editRequest.setCreatedBy(courtClerkUser);
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setEditInstruction("{}");

        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var res = underTest.findById(editRequest.getId());
        assertThat(res).isNotNull();
        assertThat(res.getId()).isEqualTo(editRequest.getId());
        assertThat(res.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findByIdNotLocked(editRequest.getId());
    }

    @Test
    @DisplayName("Should throw error when requested request does not exist")
    void findByIdNotFound() {
        var id = UUID.randomUUID();
        when(editRequestRepository.findByIdNotLocked(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.findById(id)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
    }

    @Test
    @DisplayName("Should return new create recording dto for the edit request")
    void createRecordingSuccess() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setEditInstruction("{}");
        editRequest.setSourceRecording(mockRecording);

        when(mockRecording.getFilename()).thenReturn("index.mp4");
        when(recordingService.getNextVersionNumber(mockParentRecId)).thenReturn(2);

        var dto = underTest.createRecordingDto(mockRecordingId, "index.mp4", editRequest);

        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(mockRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(mockParentRecId);
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getEditInstructions())
            .isEqualTo(format("{\"editRequestId\":\"%s\",\"editInstructions\":{\"requestedInstructions\":null,"
                                  + "\"ffmpegInstructions\":null}}", editRequest.getId()));

        assertThat(dto.getCaptureSessionId()).isEqualTo(mockCaptureSessionId);
        assertThat(dto.getFilename()).isEqualTo("index.mp4");

        verify(recordingService, times(1)).getNextVersionNumber(mockParentRecId);
    }

    @Test
    @DisplayName("Should return create recording dto with parent recording")
    void createRecordingDtoWithParentRecording() {
        when(mockRecording.getFilename()).thenReturn("source.mp4");

        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        editRequest.setEditInstruction("""
            {
                "requestedInstructions": [ ],
                "ffmpegInstructions": [ ]
            }
            """);

        var newRecordingId = UUID.randomUUID();
        when(recordingService.getNextVersionNumber(mockParentRecId)).thenReturn(3);

        var dto = underTest.createRecordingDto(newRecordingId, "newFile.mp4", editRequest);
        assertThat(dto.getId()).isEqualTo(newRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(mockParentRecId);
        assertThat(dto.getFilename()).isEqualTo("newFile.mp4");
        assertThat(dto.getVersion()).isEqualTo(3);
        assertThat(dto.getEditInstructions())
            .isEqualTo("{\"editRequestId\":null,\"editInstructions\":{\"requestedInstructions\":[],"
                           + "\"ffmpegInstructions\":[]}}");
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container")
    void generateAssetSourceContainerNotFound() {
        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Container (" + sourceContainer + ") does not exist");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container's mp4")
    void generateAssetSourceContainerMp4NotFound() {
        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("MP4 file not found in container " + sourceContainer))
            .when(azureIngestStorageService).getMp4FileName(sourceContainer);

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + sourceContainer);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verifyNoMoreInteractions(azureIngestStorageService);
    }

    @Test
    @DisplayName("Should throw error when import asset fails when generating asset")
    void generateAssetImportAssetError() throws InterruptedException {
        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("Something went wrong")).when(mediaService)
            .importAsset(any(GenerateAssetDTO.class), eq(false));

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Something went wrong");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, never()).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
    }

    @Test
    @DisplayName("Should throw error when import asset fails (returning error) when generating asset")
    void generateAssetImportAssetReturnsError() throws InterruptedException {
        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        var generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.ERROR.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);

        var message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Unknown Server Exception: Failed to generate asset for edit request: "
                           + editRequest.getSourceRecording().getId()
                           + ", new recording: "
                           + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, never()).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, never()).getMp4FileName(any());
    }

    @Test
    @DisplayName("Should throw error when generating asset if get mp4 from final fails")
    void generateAssetGetMp4FinalNotFound() throws InterruptedException {
        var editRequest = new EditRequest();
        editRequest.setSourceRecording(mockRecording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        var generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);
        doThrow(new NotFoundException("MP4 file not found in container " + newRecordingId))
            .when(azureFinalStorageService)
            .getMp4FileName(newRecordingId.toString());

        var message = assertThrows(
            NotFoundException.class,
            () -> underTest.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsProcessing(sourceContainer);
        verify(azureIngestStorageService, times(1)).markContainerAsSafeToDelete(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(any());
    }

    @Test
    @DisplayName("Search edit requests as admin user should not set additional filters")
    void findAllAsAdminUseSetsNullFilters() {
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(false);

        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(mockRecording);
        EditInstructions originalEdits = new EditInstructions(
            List.of(createCut(10, 20, "some original reason")),
            List.of(createSegment(0, 10), createSegment(20, 30)));
        editRequest.setEditInstruction(underTest.toJson(originalEdits));
        editRequest.setCreatedBy(courtClerkUser);

        SearchEditRequests params = new SearchEditRequests();
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(new PageImpl<>(List.of(editRequest)));

        Page<EditRequestDTO> result = underTest.findAll(params, Pageable.unpaged());

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(editRequestRepository).searchAllBy(
            argThat(search ->
                        search.getAuthorisedBookings() == null
                            && search.getAuthorisedCourt() == null),
            any(Pageable.class));
    }

    @Test
    @DisplayName("Search edit requests as app user should set additional filters")
    void findAllAsAppUserSetsCourtFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.isPortalUser()).thenReturn(false);
        when(mockAuth.getCourtId()).thenReturn(UUID.randomUUID());

        SearchEditRequests params = new SearchEditRequests();
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(Page.empty());

        underTest.findAll(params, Pageable.unpaged());

        verify(editRequestRepository).searchAllBy(
            argThat(p ->
                        p.getAuthorisedBookings() == null
                            && p.getAuthorisedCourt().equals(mockAuth.getCourtId())),
            any(Pageable.class));
    }

    @Test
    @DisplayName("Search edit requests as portal user should set additional filters")
    void findAllAsPortalUserSetsAuthedBookingFilterOnly() {
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(false);
        when(mockAuth.isPortalUser()).thenReturn(true);
        when(mockAuth.getSharedBookings()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(Page.empty());

        SearchEditRequests params = new SearchEditRequests();
        underTest.findAll(params, Pageable.unpaged());

        verify(editRequestRepository).searchAllBy(
            argThat(p ->
                        p.getAuthorisedBookings().containsAll(mockAuth.getSharedBookings())
                            && p.getAuthorisedCourt() == null),
            any(Pageable.class));
    }

    @Test
    @DisplayName("Should combine instructions correctly with single new command")
    void combineCutsOnOriginalTimelineSingleCommand() {
        var originallyKeptSegments = List.of(createSegment(0, 10), createSegment(20, 30));
        var originalCutSegments = List.of(createCut(10, 20, "some reason"));
        var originalInstructions = new EditInstructions(originalCutSegments, originallyKeptSegments);

        // Cut at 5–8 in the edited timeline mapping to 5–8 in the original timeline
        var newCut = createCut(5, 8, "test");

        var result = underTest.combineCutsOnOriginalTimeline(originalInstructions, List.of(newCut));

        assertThat(result).hasSize(2);

        assertThat(result.getFirst().getStart()).isEqualTo(5);
        assertThat(result.getFirst().getEnd()).isEqualTo(8);
        assertThat(result.getFirst().getReason()).isEqualTo("test");

        assertThat(result.getLast().getStart()).isEqualTo(10);
        assertThat(result.getLast().getEnd()).isEqualTo(20);
        assertThat(result.getLast().getReason()).isEqualTo("some reason");
    }

    @Test
    @DisplayName("Should combine instructions correctly with cuts across multiple segments")
    void combineCutsOnOriginalTimelineMapsCutThatSpanningMultipleSegments() {
        var originallyKeptSegments = List.of(createSegment(0, 10), createSegment(20, 30));
        var originalCutSegments = List.of(createCut(10, 20, "some reason"));
        var originalInstructions = new EditInstructions(originalCutSegments, originallyKeptSegments);

        // Cut at 8–12 in the edited timeline:
        // - 8–10 -> 8–10 in original (segment 1)
        // - 10–12 -> 20–22 in original (segment 2)
        var newCut = createCut(8, 12, "test");

        var result = underTest.combineCutsOnOriginalTimeline(originalInstructions, List.of(newCut));

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().getStart()).isEqualTo(8);
        assertThat(result.getFirst().getEnd()).isEqualTo(22);
        assertThat(result.getFirst().getReason()).isEqualTo("test");
    }

    @Test
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination false")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationFalse() {
        // when editing an edit from legacy editing
        when(mockRecording.getFilename()).thenReturn("filename.mp4");
        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(30));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(mockRecordingId);
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(10, 20, "some reason"))));

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(request.getId())).thenReturn(Optional.of(new EditRequest()));

        UpsertResult result = underTest.upsert(request);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);

        verify(editRequestRepository, times(1)).save(captor.capture());

        EditRequest editRequest = captor.getValue();
        assertThat(editRequest.getId()).isEqualTo(request.getId());
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(mockRecordingId);
        assertThat(editRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(editRequest.getEditInstruction()).isNotNull();

        EditInstructions editInstructions = EditInstructions.tryFromJson(editRequest.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).hasSize(1);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getStart()).isEqualTo(10);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getEnd()).isEqualTo(20);

        assertThat(editInstructions.getFfmpegInstructions()).isNotNull();

        assertEditInstructionsEq(List.of(createSegment(0, 10), createSegment(20, 30)),
                                 editInstructions.getFfmpegInstructions());
    }

    @Test
    @DisplayName("Should throw exception when editInstructions is null for *new* edit request")
    void validateEditInstructionsIsEmptyForNewEditRequest() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(recording.getId());
        dto.setStatus(EditRequestStatus.DRAFT);
        dto.setEditInstructions(new ArrayList<>());

        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        var message = assertThrows(
            BadRequestException.class,
            () -> underTest.upsert(dto)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot create an edit request with empty instructions");
    }

    @Test
    @DisplayName("Should delete edit instruction when editInstructions is empty for *existing* edit request")
    void validateEditInstructionsIsEmpty() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(recording.getId());
        dto.setEditInstructions(List.of());
        dto.setStatus(EditRequestStatus.DRAFT);
        dto.setEditInstructions(new ArrayList<>());

        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(mockEditRequest));

        UpsertResult upsertResult = underTest.upsert(dto);

        assertThat(upsertResult).isEqualTo(UpsertResult.UPDATED);

        verify(editRequestRepository, times(1)).delete(mockEditRequest);
    }

    @DisplayName("Should be able to upsert edit instructions with CSV file")
    void upsertEditInstructionsWithCSVFile() {

        final String fileContents = """
            Edit Number,Start time of cut,End time of cut,Total time removed,Reason
            1,00:00:00,00:00:30,00:30:00,first thirty seconds reason
            2,00:01:01,00:02:00,00:00:59,
            """;

        final String expectedEditInstructions = """
            {
                "requestedInstructions": [
                  {
                    "start_of_cut": "00:00:00",
                    "end_of_cut": "00:00:30",
                    "reason": "first thirty seconds reason",
                    "start": 0,
                    "end": 30
                  },
                  {
                    "start_of_cut": "00:01:01",
                    "end_of_cut": "00:02:00",
                    "reason": "",
                    "start": 61,
                    "end": 120
                  }
                ],
                "ffmpegInstructions": [
                  {
                    "start": 30,
                    "end": 61
                  },
                  {
                    "start": 120,
                    "end": 180
                  }
                ]
              }
            """;

        final MockMultipartFile file = new MockMultipartFile(
            "file", "edit_instructions.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        EditRequest returnedByDb = new EditRequest();
        returnedByDb.setCreatedBy(courtClerkUser);

        when(editRequestRepository.findById(any())).thenReturn(Optional.of(returnedByDb));
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId)).thenReturn(Optional.of(mockRecording));

        underTest.upsert(mockRecordingId, file);

        ArgumentCaptor<EditRequest> savedEditRequest = ArgumentCaptor.forClass(EditRequest.class);
        verify(editRequestRepository, times(1)).save(savedEditRequest.capture());

        assertThat(savedEditRequest.getValue().getId()).isNotNull();
        assertThat(savedEditRequest.getValue().getSourceRecording()).isEqualTo(mockRecording);
        assertThat(savedEditRequest.getValue().getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(savedEditRequest.getValue().getCreatedBy()).isEqualTo(courtClerkUser);

        JSONAssert.assertEquals(
            expectedEditInstructions, savedEditRequest.getValue().getEditInstruction(), JSONCompareMode.LENIENT);
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
            NotFoundException.class,
            () -> underTest.upsert(mockRecordingId, file)
        );
    }

    @DisplayName("Should throw an exception if updating edit instructions with empty file")
    @Test
    void upsertEditInstructionsWithEmptyFile() {
        final String fileContents = """
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "edits.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        assertThrows(
            NotFoundException.class,
            () -> underTest.upsert(mockRecordingId, file)
        );
    }

    @Test
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination true")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationTrue() {
        // when editing an edit from the new editing process
        final EditInstructions originalEdits = new EditInstructions(
            List.of(createCut(10, 20, "some original reason")),
            List.of(createSegment(0, 10), createSegment(20, 30)));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(mockRecordingId);
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(5, 8, "some new reason"))));

        when(mockRecording.getFilename()).thenReturn("filename.mp4");
        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(27));
        when(mockParentRecording.getDuration()).thenReturn(Duration.ofSeconds(30));
        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));
        when(mockRecording.getEditInstruction()).thenReturn(underTest.toJson(originalEdits));
        when(editRequestRepository.findById(request.getId())).thenReturn(Optional.of(new EditRequest()));
        when(mockAuth.isAppUser()).thenReturn(true);

        UpsertResult result = underTest.upsert(request);

        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);

        verify(editRequestRepository, times(1)).save(captor.capture());

        EditRequest editRequest = captor.getValue();
        assertThat(editRequest.getId()).isEqualTo(request.getId());
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(mockParentRecId);
        assertThat(editRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(editRequest.getEditInstruction()).isNotNull();

        EditInstructions editInstructions = EditInstructions.tryFromJson(editRequest.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).hasSize(2);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getStart()).isEqualTo(5);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getEnd()).isEqualTo(8);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getReason()).isEqualTo("some new reason");
        assertThat(editInstructions.getRequestedInstructions().getLast().getStart()).isEqualTo(10);
        assertThat(editInstructions.getRequestedInstructions().getLast().getEnd()).isEqualTo(20);
        assertThat(editInstructions.getRequestedInstructions().getLast().getReason()).isEqualTo("some original reason");

        assertThat(editInstructions.getFfmpegInstructions()).isNotNull();

        assertEditInstructionsEq(List.of(createSegment(0, 5), createSegment(8, 10), createSegment(20, 30)),
                                 editInstructions.getFfmpegInstructions());
    }

    @Test
    @DisplayName("Should trigger request submission jointly agreed email on submission")
    void upsertOnSubmittedJointlyAgreed() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecordingId);
        dto.setStatus(EditRequestStatus.SUBMITTED);
        dto.setEditInstructions(instructions);
        dto.setJointlyAgreed(true);

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.DRAFT);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
        verify(editNotificationService, times(1)).onEditRequestSubmitted(editRequest);
    }

    @Test
    @DisplayName("Should trigger request submission not jointly agreed email on submission")
    void upsertOnSubmittedNotJointlyAgreed() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecordingId);
        dto.setStatus(EditRequestStatus.SUBMITTED);
        dto.setEditInstructions(instructions);
        dto.setJointlyAgreed(false);

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.DRAFT);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
        verify(editNotificationService, times(1)).onEditRequestSubmitted(editRequest);
    }

    @Test
    @DisplayName("Should trigger request rejection email on edit request rejection")
    void upsertOnRejected() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecordingId);
        dto.setStatus(EditRequestStatus.REJECTED);
        dto.setEditInstructions(instructions);
        dto.setJointlyAgreed(false);

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.SUBMITTED);

        when(recordingRepository.findByIdAndDeletedAtIsNull(mockRecordingId))
            .thenReturn(Optional.of(mockRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = underTest.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(mockRecordingId);
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
        verify(editNotificationService, times(1)).onEditRequestRejected(editRequest);
    }


    private static void assertEditInstructionsEq(List<FfmpegEditInstructionDTO> expected,
                                                 List<FfmpegEditInstructionDTO> actual) {
        assertThat(actual.size()).isEqualTo(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i).getStart()).isEqualTo(expected.get(i).getStart());
            assertThat(actual.get(i).getEnd()).isEqualTo(expected.get(i).getEnd());
        }
    }

    private static FfmpegEditInstructionDTO createSegment(long start, long end) {
        return new FfmpegEditInstructionDTO(start, end);
    }

    private static EditCutInstructionDTO createCut(long start, long end, String reason) {
        return new EditCutInstructionDTO(start, end, reason);
    }
}
