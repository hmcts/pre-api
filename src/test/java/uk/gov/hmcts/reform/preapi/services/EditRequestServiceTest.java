package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import uk.gov.hmcts.reform.preapi.entities.User;
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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

    @Autowired
    private EditRequestService editRequestService;

    @BeforeEach
    void setup() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
    }

    @Test
    @DisplayName("Should return all pending edit requests")
    void getPendingEditRequestsSuccess() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        when(editRequestRepository.findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING))
            .thenReturn(Optional.of(editRequest));

        var res = editRequestService.getNextPendingEditRequest();

        assertThat(res).isPresent();
        assertThat(res.get().getId()).isEqualTo(editRequest.getId());
        assertThat(res.get().getStatus()).isEqualTo(EditRequestStatus.PENDING);

        verify(editRequestRepository, times(1)).findFirstByStatusIsOrderByCreatedAt(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should attempt to perform edit request and return error on ffmpeg service error")
    void performEditFfmpegError() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setSourceRecording(recording);

        doThrow(UnknownServerException.class)
            .when(ffmpegService).performEdit(any(UUID.class), eq(editRequest));

        assertThrows(
            Exception.class,
            () -> editRequestService.performEdit(editRequest)
        );

        verify(editRequestRepository, times(1)).saveAndFlush(any(EditRequest.class));
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any());
    }

    @Test
    @DisplayName("Should perform edit request and return created recording")
    void performEditSuccess() throws InterruptedException {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setSourceRecording(recording);

        when(recordingService.getNextVersionNumber(recording.getId())).thenReturn(2);
        when(recordingService.upsert(any(CreateRecordingDTO.class))).thenReturn(UpsertResult.CREATED);
        var newRecordingDto = new RecordingDTO();
        newRecordingDto.setParentRecordingId(recording.getId());
        when(recordingService.findById(any(UUID.class))).thenReturn(newRecordingDto);
        when(azureIngestStorageService.doesContainerExist(anyString())).thenReturn(true);
        var importResponse = new GenerateAssetResponseDTO();
        importResponse.setJobStatus(JobState.FINISHED.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(importResponse);
        when(azureFinalStorageService.getMp4FileName(anyString())).thenReturn("index.mp4");

        var res = editRequestService.performEdit(editRequest);
        assertThat(res).isNotNull();
        assertThat(res.getParentRecordingId()).isEqualTo(recording.getId());

        verify(editRequestRepository, times(1)).saveAndFlush(any(EditRequest.class));
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, times(1)).findById(any(UUID.class));
        verify(azureIngestStorageService, times(1)).doesContainerExist(anyString());
        verify(azureIngestStorageService, times(1)).getMp4FileName(anyString());
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(anyString());
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        var id = UUID.randomUUID();

        when(editRequestRepository.findById(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.markAsProcessing(id)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findById(id);
        verify(editRequestRepository, never()).saveAndFlush(any(EditRequest.class));
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
            () -> editRequestService.markAsProcessing(editRequest.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + editRequest.getId()
                           + ") is in a PROCESSING state. Expected state is PENDING.");

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).saveAndFlush(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw lock error when encounters locked edit request")
    void performEditLocked() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        doThrow(PessimisticLockingFailureException.class).when(editRequestRepository).findById(editRequest.getId());

        assertThrows(
            PessimisticLockingFailureException.class,
            () -> editRequestService.markAsProcessing(editRequest.getId())
        );

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, never()).saveAndFlush(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should create a new edit request")
    void createEditRequestSuccess() {
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setDuration(Duration.ofMinutes(3));

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.empty());

        var response = editRequestService.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.CREATED);

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(sourceRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, times(1)).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should update an edit request")
    void updateEditRequestSuccess() {
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setDuration(Duration.ofMinutes(3));

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(dto.getId())).thenReturn(Optional.of(editRequest));

        var response = editRequestService.upsert(dto);
        assertThat(response).isEqualTo(UpsertResult.UPDATED);

        assertThat(editRequest.getId()).isEqualTo(dto.getId());
        assertThat(editRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(sourceRecording.getId());
        assertThat(editRequest.getEditInstruction())
            .contains("\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]");

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(sourceRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
        verify(editRequestRepository, times(1)).findById(dto.getId());
        verify(mockAuth, never()).getAppAccess();
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
    }

    @Test
    @DisplayName("Should throw not found when source recording does not exist")
    void createEditRequestSourceRecordingNotFound() {
        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(UUID.randomUUID())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(dto.getSourceRecordingId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.upsert(dto)
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
        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());

        var mockAuth = mock(UserAuthentication.class);
        var appAccess = new AppAccess();
        var user = new User();
        user.setId(UUID.randomUUID());
        appAccess.setUser(user);

        when(mockAuth.getAppAccess()).thenReturn(appAccess);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());

        var dto = CreateEditRequestDTO.builder()
            .id(UUID.randomUUID())
            .sourceRecordingId(sourceRecording.getId())
            .status(EditRequestStatus.PENDING)
            .editInstructions(instructions)
            .build();

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> editRequestService.upsert(dto)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Source Recording (" + dto.getSourceRecordingId() + ") does not have a valid duration");

        verify(recordingService, times(1)).syncRecordingMetadataWithStorage(sourceRecording.getId());
        verify(recordingRepository, times(1)).findByIdAndDeletedAtIsNull(sourceRecording.getId());
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

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot cut an entire recording: Start(0), End(180), "
                           + "Recording Duration(180)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void invertInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(60L)
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: Start(60), End(60)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void invertInstructionsBadRequestEndLTStart() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(50L)
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with end time before start time: Start(60), End(50)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void invertInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(200L) // duration is 180
                             .build());

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction end time exceeding duration: Start(60), End(200), "
                           + "Recording Duration(180)");
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

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));
        var message = assertThrows(
            BadRequestException.class,
            () -> editRequestService.invertInstructions(instructions, recording)
        ).getMessage();

        assertThat(message).isEqualTo("Overlapping instructions: Previous End(30), Current Start(20)");
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

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions1, recording));
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

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions1, recording));
    }

    @Test
    @DisplayName("Should return edit request when it exists")
    void findByIdSuccess() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setVersion(1);
        recording.setCaptureSession(captureSession);
        recording.setDuration(Duration.ofMinutes(3));
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        var user = new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);
        editRequest.setStatus(EditRequestStatus.PENDING);
        editRequest.setEditInstruction("{}");

        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var res = editRequestService.findById(editRequest.getId());
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
            () -> editRequestService.findById(id)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
    }

    @Test
    @DisplayName("Should return new create recording dto for the edit request")
    void createRecordingSuccess() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());

        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setVersion(1);
        recording.setCaptureSession(captureSession);
        recording.setFilename("index.mp4");

        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.COMPLETE);
        editRequest.setEditInstruction("{}");
        editRequest.setSourceRecording(recording);

        var newRecordingId = UUID.randomUUID();

        when(recordingService.getNextVersionNumber(recording.getId())).thenReturn(2);

        var dto = editRequestService.createRecordingDto(newRecordingId, "index.mp4", editRequest);
        assertThat(dto).isNotNull();
        assertThat(dto.getId()).isEqualTo(newRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(recording.getId());
        assertThat(dto.getVersion()).isEqualTo(2);
        assertThat(dto.getEditInstructions()).isEqualTo("{}");
        assertThat(dto.getCaptureSessionId()).isEqualTo(captureSession.getId());
        assertThat(dto.getFilename()).isEqualTo("index.mp4");

        verify(recordingService, times(1)).getNextVersionNumber(recording.getId());
    }

    @Test
    @DisplayName("Should return create recording dto with parent recording")
    void createRecordingDtoWithParentRecording() {
        var parentRecording = new Recording();
        parentRecording.setId(UUID.randomUUID());

        var sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setParentRecording(parentRecording);
        sourceRecording.setCaptureSession(new CaptureSession());
        sourceRecording.setFilename("source.mp4");

        var editRequest = new EditRequest();
        editRequest.setSourceRecording(sourceRecording);
        editRequest.setEditInstruction("{\"key\": \"value\"}");

        var newRecordingId = UUID.randomUUID();
        when(recordingService.getNextVersionNumber(parentRecording.getId())).thenReturn(3);

        var dto = editRequestService.createRecordingDto(newRecordingId, "newFile.mp4", editRequest);
        assertThat(dto.getId()).isEqualTo(newRecordingId);
        assertThat(dto.getParentRecordingId()).isEqualTo(parentRecording.getId());
        assertThat(dto.getFilename()).isEqualTo("newFile.mp4");
        assertThat(dto.getVersion()).isEqualTo(3);
        assertThat(dto.getEditInstructions()).isEqualTo("{\"key\": \"value\"}");
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container")
    void generateAssetSourceContainerNotFound() {
        var editRequest = new EditRequest();
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Source Container (" + sourceContainer + ") does not exist");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
    }

    @Test
    @DisplayName("Should throw not found when generate asset cannot find source container's mp4")
    void generateAssetSourceContainerMp4NotFound() {
        var editRequest = new EditRequest();
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("MP4 file not found in container " + sourceContainer))
            .when(azureIngestStorageService).getMp4FileName(sourceContainer);

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + sourceContainer);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
    }

    @Test
    @DisplayName("Should throw error when import asset fails when generating asset")
    void generateAssetImportAssetError() throws InterruptedException {
        var editRequest = new EditRequest();
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        doThrow(new NotFoundException("Something went wrong")).when(mediaService).importAsset(any(), eq(false));

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Something went wrong");

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(), eq(false));
    }

    @Test
    @DisplayName("Should throw error when import asset fails (returning error) when generating asset")
    void generateAssetImportAssetReturnsError() throws InterruptedException {
        var editRequest = new EditRequest();
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        var newRecordingId = UUID.randomUUID();
        var sourceContainer = newRecordingId + "-input";

        when(azureIngestStorageService.doesContainerExist(sourceContainer)).thenReturn(true);
        var generateResponse = new GenerateAssetResponseDTO();
        generateResponse.setJobStatus(JobState.ERROR.toString());
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(false))).thenReturn(generateResponse);

        var message = assertThrows(
            UnknownServerException.class,
            () -> editRequestService.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Unknown Server Exception: Failed to generate asset for edit request: "
                           + editRequest.getSourceRecording().getId()
                           + ", new recording: "
                           + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(), eq(false));
        verify(azureFinalStorageService, never()).getMp4FileName(any());
    }

    @Test
    @DisplayName("Should throw error when generating asset if get mp4 from final fails")
    void generateAssetGetMp4FinalNotFound() throws InterruptedException {
        var editRequest = new EditRequest();
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
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
            () -> editRequestService.generateAsset(newRecordingId, editRequest)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: MP4 file not found in container " + newRecordingId);

        verify(azureIngestStorageService, times(1)).doesContainerExist(sourceContainer);
        verify(azureIngestStorageService, times(1)).getMp4FileName(sourceContainer);
        verify(mediaService, times(1)).importAsset(any(), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(any());
    }

    @Test
    @DisplayName("Search edit requests as admin user should not set additional filters")
    void findAllAsAdminUseSetsNullFilters() {
        UserAuthentication auth = mock(UserAuthentication.class);
        when(auth.isAdmin()).thenReturn(true);
        when(auth.isAppUser()).thenReturn(false);
        when(auth.isPortalUser()).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setName("Example Court");
        Case aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setCourt(court);
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        CaptureSession captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        EditRequest editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        User user = new User();
        user.setId(UUID.randomUUID());
        editRequest.setCreatedBy(user);

        SearchEditRequests params = new SearchEditRequests();
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(new PageImpl<>(List.of(editRequest)));

        Page<EditRequestDTO> result = editRequestService.findAll(params, Pageable.unpaged());

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
        UserAuthentication auth = mock(UserAuthentication.class);
        when(auth.isAdmin()).thenReturn(false);
        when(auth.isAppUser()).thenReturn(true);
        when(auth.isPortalUser()).thenReturn(false);
        when(auth.getCourtId()).thenReturn(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(auth);

        SearchEditRequests params = new SearchEditRequests();
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(Page.empty());

        editRequestService.findAll(params, Pageable.unpaged());

        verify(editRequestRepository).searchAllBy(
            argThat(p ->
                        p.getAuthorisedBookings() == null
                        && p.getAuthorisedCourt().equals(auth.getCourtId())),
            any(Pageable.class));
    }

    @Test
    @DisplayName("Search edit requests as portal user should set additional filters")
    void findAllAsPortalUserSetsAuthedBookingFilterOnly() {
        UserAuthentication auth = mock(UserAuthentication.class);
        when(auth.isAdmin()).thenReturn(false);
        when(auth.isAppUser()).thenReturn(false);
        when(auth.isPortalUser()).thenReturn(true);
        when(auth.getSharedBookings()).thenReturn(List.of(UUID.randomUUID(), UUID.randomUUID()));
        SecurityContextHolder.getContext().setAuthentication(auth);

        SearchEditRequests params = new SearchEditRequests();
        when(editRequestRepository.searchAllBy(any(), any())).thenReturn(Page.empty());

        editRequestService.findAll(params, Pageable.unpaged());

        verify(editRequestRepository).searchAllBy(
            argThat(p ->
                        p.getAuthorisedBookings().containsAll(auth.getSharedBookings())
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

        var result = editRequestService.combineCutsOnOriginalTimeline(originalInstructions, List.of(newCut));

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

        var result = editRequestService.combineCutsOnOriginalTimeline(originalInstructions, List.of(newCut));

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().getStart()).isEqualTo(8);
        assertThat(result.getFirst().getEnd()).isEqualTo(22);
        assertThat(result.getFirst().getReason()).isEqualTo("test");
    }

    @Test
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination false")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationFalse() {
        // when editing an edit from legacy editing
        Recording parentRecording = new Recording();
        parentRecording.setId(UUID.randomUUID());

        Recording sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setParentRecording(parentRecording);
        sourceRecording.setFilename("filename.mp4");
        sourceRecording.setDuration(Duration.ofSeconds(30));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(sourceRecording.getId());
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(10, 20, "some reason"))));

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(request.getId())).thenReturn(Optional.of(new EditRequest()));

        UpsertResult result = editRequestService.upsert(request);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);

        verify(editRequestRepository, times(1)).save(captor.capture());

        EditRequest editRequest = captor.getValue();
        assertThat(editRequest.getId()).isEqualTo(request.getId());
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(sourceRecording.getId());
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
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination true")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationTrue() {
        // when editing an edit from the new editing process
        Recording parentRecording = new Recording();
        parentRecording.setId(UUID.randomUUID());
        parentRecording.setFilename("filename.mp4");
        parentRecording.setDuration(Duration.ofSeconds(30));

        Recording sourceRecording = new Recording();
        sourceRecording.setId(UUID.randomUUID());
        sourceRecording.setParentRecording(parentRecording);
        sourceRecording.setFilename("filename.mp4");
        sourceRecording.setDuration(Duration.ofSeconds(27));
        EditInstructions originalEdits = new EditInstructions(
            List.of(createCut(10, 20, "some original reason")),
            List.of(createSegment(0, 10), createSegment(20, 30)));
        sourceRecording.setEditInstruction(editRequestService.toJson(originalEdits));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(sourceRecording.getId());
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(5, 8, "some new reason"))));

        when(recordingRepository.findByIdAndDeletedAtIsNull(sourceRecording.getId()))
            .thenReturn(Optional.of(sourceRecording));
        when(editRequestRepository.findById(request.getId())).thenReturn(Optional.of(new EditRequest()));

        UpsertResult result = editRequestService.upsert(request);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        ArgumentCaptor<EditRequest> captor = ArgumentCaptor.forClass(EditRequest.class);

        verify(editRequestRepository, times(1)).save(captor.capture());

        EditRequest editRequest = captor.getValue();
        assertThat(editRequest.getId()).isEqualTo(request.getId());
        assertThat(editRequest.getSourceRecording().getId()).isEqualTo(parentRecording.getId());
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
