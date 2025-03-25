package uk.gov.hmcts.reform.preapi.services;

import com.azure.resourcemanager.mediaservices.models.JobState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.IEmailService;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.IMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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

    @MockitoBean
    private EmailServiceFactory emailServiceFactory;

    @MockitoBean
    private IEmailService emailService;

    @Autowired
    private EditRequestService editRequestService;

    @BeforeEach
    void setup() {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(emailService);
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

        assertThat(res.isPresent()).isTrue();
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
        editRequest.setStatus(EditRequestStatus.PROCESSING);
        editRequest.setSourceRecording(recording);

        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));
        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));
        doThrow(UnknownServerException.class)
            .when(ffmpegService).performEdit(any(UUID.class), eq(editRequest));

        assertThrows(
            Exception.class,
            () -> editRequestService.performEdit(editRequest.getId())
        );

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, times(1)).findByIdNotLocked(editRequest.getId());
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any());
    }

    @Test
    @DisplayName("Should perform edit request and return created recording")
    void performEditSuccess() throws InterruptedException {
        var user1 = new User();
        var user2 = new User();
        var share1 = new ShareBooking();
        share1.setSharedWith(user1);
        var share2 = new ShareBooking();
        share2.setSharedWith(user2);
        var aCase = new Case();
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);
        booking.setShares(Set.of(share1, share2));
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PROCESSING);
        editRequest.setStartedAt(Timestamp.from(Instant.now()));
        editRequest.setSourceRecording(recording);

        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));
        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));
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

        var res = editRequestService.performEdit(editRequest.getId());
        assertThat(res).isNotNull();
        assertThat(res.getParentRecordingId()).isEqualTo(recording.getId());

        verify(editRequestRepository, times(1)).findById(editRequest.getId());
        verify(editRequestRepository, times(1)).findByIdNotLocked(editRequest.getId());
        verify(editRequestRepository, times(1)).save(any(EditRequest.class));
        verify(ffmpegService, times(1)).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, times(1)).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, times(1)).findById(any(UUID.class));
        verify(azureIngestStorageService, times(1)).doesContainerExist(anyString());
        verify(azureIngestStorageService, times(1)).getMp4FileName(anyString());
        verify(mediaService, times(1)).importAsset(any(GenerateAssetDTO.class), eq(false));
        verify(azureFinalStorageService, times(1)).getMp4FileName(anyString());
        verify(emailService, times(1)).recordingEdited(user1, aCase);
        verify(emailService, times(1)).recordingEdited(user2, aCase);
    }

    @Test
    @DisplayName("Should throw not found error when edit request cannot be found with specified id")
    void performEditNotFound() {
        var id = UUID.randomUUID();

        when(editRequestRepository.findByIdNotLocked(id)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> editRequestService.performEdit(id)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Edit Request: " + id);

        verify(editRequestRepository, times(1)).findByIdNotLocked(id);
        verify(editRequestRepository, never()).save(any(EditRequest.class));
        verify(ffmpegService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
    }

    @Test
    @DisplayName("Should not perform edit and return null when status of edit request is not PROCESSING")
    void performEditStatusNotProcessing() {
        var editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setStatus(EditRequestStatus.PENDING);

        when(editRequestRepository.findByIdNotLocked(editRequest.getId())).thenReturn(Optional.of(editRequest));
        when(editRequestRepository.findById(editRequest.getId())).thenReturn(Optional.of(editRequest));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> editRequestService.performEdit(editRequest.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Resource EditRequest("
                           + editRequest.getId()
                           + ") is in a PENDING state. Expected state is PROCESSING.");

        verify(editRequestRepository, times(1)).findByIdNotLocked(editRequest.getId());
        verify(editRequestRepository, never()).save(any(EditRequest.class));
        verify(ffmpegService, never()).performEdit(any(UUID.class), any(EditRequest.class));
        verify(recordingService, never()).upsert(any(CreateRecordingDTO.class));
        verify(recordingService, never()).findById(any(UUID.class));
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
    @DisplayName("Should return inverted instructions (ordered correctly)")
    void invertInstructionsSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
                             .start(60L)
                             .end(120L)
                             .build());
        instructions1.add(EditCutInstructionDTO.builder()
                              .start(61L)
                              .end(121L)
                              .build());



        List<EditCutInstructionDTO> instructions2 = new ArrayList<>();
        instructions2.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(120L)
                              .build());
        instructions2.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(121L)
                              .build());

        List<EditCutInstructionDTO> instructions3 = new ArrayList<>();
        instructions3.add(EditCutInstructionDTO.builder()
                              .start(61L)
                              .end(70L)
                              .build());
        instructions3.add(EditCutInstructionDTO.builder()
                              .start(60L)
                              .end(121L)
                              .build());

        var expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(121)
                .end(180)
                .build()
        );

        var recording = new Recording();
        recording.setDuration(Duration.ofMinutes(3));

        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions1, recording));
        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions2, recording));
        assertEditInstructionsEq(expectedInvertedInstructions,
                                 editRequestService.invertInstructions(instructions3, recording));
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

    private void assertEditInstructionsEq(List<FfmpegEditInstructionDTO> expected,
                                          List<FfmpegEditInstructionDTO> actual) {
        assertThat(actual.size()).isEqualTo(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i).getStart()).isEqualTo(expected.get(i).getStart());
            assertThat(actual.get(i).getEnd()).isEqualTo(expected.get(i).getEnd());
        }
    }
}
