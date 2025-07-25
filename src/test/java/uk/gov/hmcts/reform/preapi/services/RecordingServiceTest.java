package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.RecordingNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordingService.class)
@SuppressWarnings({ "PMD.LawOfDemeter", "checkstyle:LineLength"})
class RecordingServiceTest {
    private static Recording recordingEntity;
    private static CaptureSession captureSession;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private CaptureSessionRepository captureSessionRepository;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private GovNotify govNotify;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @Autowired
    private RecordingService recordingService;

    @BeforeAll
    static void setUp() {
        recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());
        Booking bookingEntity = new Booking();
        bookingEntity.setId(UUID.randomUUID());
        bookingEntity.setCaseId(HelperFactory.createCase(
            HelperFactory.createCourt(CourtType.CROWN, "Test Court", "TC"),
            "Test Case",
            false,
            null)
        );

        captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(bookingEntity);
        var user = new User();
        user.setId(UUID.randomUUID());
        captureSession.setFinishedByUser(user);
        captureSession.setStartedByUser(user);

        recordingEntity.setCaptureSession(captureSession);
        recordingEntity.setVersion(1);
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));
    }

    @BeforeEach
    void reset() {
        recordingEntity.setDeletedAt(null);
        recordingEntity.setParentRecording(null);
        recordingEntity.setCaptureSession(captureSession);
    }

    @DisplayName("Find a recording by it's id and return a model")
    @Test
    void findRecordingByIdSuccess() {
        when(
            recordingRepository.findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        var model = recordingService.findById(recordingEntity.getId());
        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSession().getId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a recording by it's id which is missing")
    @Test
    void findRecordingByIdMissing() {
        when(
            recordingRepository.findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            )
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> recordingService.findById(recordingEntity.getId())
        );

        verify(recordingRepository, times(1))
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            );
    }

    @DisplayName("Find a list of recordings and return a list of models")
    @Test
    void findAllRecordingsSuccess() {
        var params = new SearchRecordings();
        when(
            recordingRepository.searchAllBy(eq(params), eq(false), any())
        ).thenReturn(new PageImpl<>(List.of(recordingEntity)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = recordingService.findAll(params, false, null).get().toList();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(recordingEntity.getId());
        assertThat(modelList.getFirst().getCaptureSession().getId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a list of recordings filtered by startedAt and return a list of models")
    @Test
    void findAllRecordingsStartedAtSuccess() {
        var params = new SearchRecordings();
        params.setStartedAtFrom(Timestamp.valueOf("2023-01-01 00:00:00"));
        params.setStartedAtUntil(Timestamp.valueOf("2023-01-01 23:59:59"));

        when(
            recordingRepository.searchAllBy(params, false, null)
        ).thenReturn(new PageImpl<>(List.of(recordingEntity)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = recordingService.findAll(params, false, null).get().toList();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(recordingEntity.getId());
        assertThat(modelList.getFirst().getCaptureSession().getId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Create a recording")
    @Test
    void createRecordingSuccess() {
        var aCase = new Case();
        aCase.setState(CaseState.OPEN);
        var booking = new Booking();
        booking.setCaseId(aCase);
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(captureSession.getId());
        recordingModel.setVersion(1);

        var recordingEntity = new Recording();
        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndDeletedAtIsNull(
                captureSession.getId()
            )
        ).thenReturn(Optional.of(captureSession));
        when(recordingRepository.findById(any())).thenReturn(Optional.empty());
        when(recordingRepository.save(recordingEntity)).thenReturn(recordingEntity);

        assertThat(recordingService.upsert(recordingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Update a recording")
    @Test
    void updateRecordingSuccess() {
        var aCase = new Case();
        aCase.setState(CaseState.OPEN);
        var booking = new Booking();
        booking.setCaseId(aCase);
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(recordingEntity.getId());
        recordingModel.setCaptureSessionId(UUID.randomUUID());
        recordingModel.setVersion(2);
        when(
            recordingRepository.findById(recordingModel.getId())
        ).thenReturn(Optional.of(recordingEntity));
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(recordingModel.getCaptureSessionId()))
            .thenReturn(Optional.of(captureSession));

        when(recordingRepository.save(recordingEntity)).thenReturn(recordingEntity);

        assertThat(recordingService.upsert(recordingModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Update a deleted recording")
    @Test
    void updateRecordingBadRequest() {
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(2);
        var recordingEntity = new Recording();
        recordingEntity.setId(recordingModel.getId());
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));

        when(
            recordingRepository.findById(recordingModel.getId())
        ).thenReturn(Optional.of(recordingEntity));

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> recordingService.upsert(recordingModel)
        );

        verify(recordingRepository, times(1))
            .findById(recordingModel.getId());
        verify(recordingRepository, never()).save(any());
    }

    @DisplayName("Fail to create recording - CaptureSession not found")
    @Test
    void createRecordingFailCaptureSessionNotFound() {
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setCaptureSessionId(UUID.randomUUID());

        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId()
            )
        ).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> recordingService.upsert(recordingModel));
    }

    @DisplayName("Fail to update recording - CaptureSession not found")
    @Test
    void updateRecordingFailCaptureSessionNotFound() {
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setCaptureSessionId(UUID.randomUUID());

        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(true);
        when(
            captureSessionRepository.findByIdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId()
            )
        ).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> recordingService.upsert(recordingModel));
    }

    @DisplayName("Fail to create/update recording when case not open")
    @Test
    void upsertRecordingCaseNotOpenBadRequest() {
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setParentRecordingId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(UUID.randomUUID());
        var aCase = new Case();
        aCase.setState(CaseState.CLOSED);
        var booking = new Booking();
        booking.setCaseId(aCase);
        var captureSession = new CaptureSession();
        captureSession.setBooking(booking);

        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId()
            )
        ).thenReturn(Optional.of(captureSession));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> recordingService.upsert(recordingModel)
        ).getMessage();
        assertThat(message)
            .isEqualTo("Resource Recording("
                           + recordingModel.getId()
                           + ") is associated with a case in the state CLOSED. Must be in state OPEN.");
    }

    @DisplayName("Fail to create recording - Parent Recording not found")
    @Test
    void createRecordingFailParentRecordingNotFound() {
        var aCase = new Case();
        aCase.setState(CaseState.OPEN);
        var booking = new Booking();
        booking.setCaseId(aCase);
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setParentRecordingId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(UUID.randomUUID());

        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId()
            )
        ).thenReturn(Optional.of(captureSession));
        when(
            recordingRepository.findById(recordingModel.getParentRecordingId())
        ).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> recordingService.upsert(recordingModel));
    }

    @DisplayName("Find a list of recordings when recording has been deleted")
    @Test
    void findAllRecordingsDeleted() {
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recordingEntity);
        var params = new SearchRecordings();
        when(recordingRepository.searchAllBy(params, false, null)).thenReturn(Page.empty());
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var models = recordingService.findAll(params, false, null).get().toList();

        verify(recordingRepository, times(1))
            .searchAllBy(params, false, null);

        assertThat(models.size()).isEqualTo(0);
    }

    @DisplayName("Delete a recording by it's id")
    @Test
    void deleteRecordingSuccess() {
        when(
            recordingRepository.findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        recordingService.deleteById(recordingEntity.getId());

        verify(recordingRepository, times(1))
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            );

        verify(recordingRepository, times(1)).saveAndFlush(recordingEntity);
    }

    @DisplayName("Delete a recording by it's id when recording doesn't exist")
    @Test
    void deleteRecordingNotFound() {
        when(
            recordingRepository
                .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                    recordingEntity.getId()
                )
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> recordingService.deleteById(recordingEntity.getId())
        );

        verify(recordingRepository, times(1))
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            );
        verify(recordingRepository, never()).deleteById(recordingEntity.getId());
    }

    @DisplayName("Delete a recording by it's id when recording has already been deleted")
    @Test
    void deleteRecordingAlreadyDeleted() {
        Timestamp now = Timestamp.from(Instant.now());
        recordingEntity.setDeletedAt(now);
        when(
            recordingRepository.findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        assertThrows(
            NotFoundException.class,
            () -> recordingService.deleteById(recordingEntity.getId())
        );

        verify(recordingRepository, times(1))
            .findByIdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNullAndCaptureSession_Booking_DeletedAtIsNull(
                recordingEntity.getId()
            );
        verify(recordingRepository, never()).deleteById(recordingEntity.getId());
    }

    @DisplayName("Should not throw error when all recordings of a capture session have been marked as deleted")
    @Test
    void deleteCascadeSuccess() {
        when(recordingRepository.existsByCaptureSessionAndDeletedAtIsNull(recordingEntity.getCaptureSession()))
            .thenReturn(false);

        assertDoesNotThrow(
            () -> recordingService.deleteCascade(recordingEntity.getCaptureSession())
        );
    }

    @DisplayName("Should throw error when all recordings of a capture session have not been marked as deleted")
    @Test
    void deleteCascadeRecordingsNotDeleted() {
        when(recordingRepository.existsByCaptureSessionAndDeletedAtIsNull(recordingEntity.getCaptureSession()))
            .thenReturn(true);

        var message = assertThrows(
            RecordingNotDeletedException.class,
            () -> recordingService.deleteCascade(recordingEntity.getCaptureSession())
        ).getMessage();

        assertThat(message).isEqualTo("Cannot delete because and associated recording has not been deleted.");
    }

    @DisplayName("Should set started at from and until when started at is set")
    @Test
    void searchRecordingsStartedAtFromUntilSet() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var params = new SearchRecordings();
        var startedAt = new Date();
        params.setStartedAt(startedAt);
        when(recordingRepository.searchAllBy(params, false, null)).thenReturn(Page.empty());

        recordingService.findAll(params, false, null);

        assertThat(params.getStartedAtFrom()).isNotNull();
        assertThat(params.getStartedAtFrom().toInstant()).isEqualTo(startedAt.toInstant());

        assertThat(params.getStartedAtUntil()).isNotNull();
        assertThat(params.getStartedAtUntil().toInstant())
            .isEqualTo(startedAt.toInstant().plus(86399, ChronoUnit.SECONDS));
    }

    @DisplayName("Should not set started at from and until when started at is not set")
    @Test
    void searchRecordingsStartedAtFromUntilNotSet() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var params = new SearchRecordings();
        when(recordingRepository.searchAllBy(params, false, null)).thenReturn(Page.empty());

        recordingService.findAll(params, false, null);

        assertThat(params.getStartedAtFrom()).isNull();

        assertThat(params.getStartedAtUntil()).isNull();
    }

    @DisplayName("Should undelete a recording successfully when recording is marked as deleted")
    @Test
    void undeleteSuccess() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setDeletedAt(Timestamp.from(Instant.now()));
        recording.setCaptureSession(captureSession);

        when(recordingRepository.findById(recording.getId())).thenReturn(Optional.of(recording));

        recordingService.undelete(recording.getId());

        verify(recordingRepository, times(1)).findById(recording.getId());
        verify(captureSessionService, times(1)).undelete(captureSession.getId());
        verify(recordingRepository, times(1)).save(recording);
    }

    @DisplayName("Should do nothing to the recording when recording is not deleted")
    @Test
    void undeleteNotDeletedSuccess() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingRepository.findById(recording.getId())).thenReturn(Optional.of(recording));

        recordingService.undelete(recording.getId());

        verify(recordingRepository, times(1)).findById(recording.getId());
        verify(captureSessionService, times(1)).undelete(captureSession.getId());
        verify(recordingRepository,never()).save(recording);
    }

    @DisplayName("Should throw not found exception when recording cannot be found")
    @Test
    void undeleteNotFound() {
        var recordingId = UUID.randomUUID();

        when(recordingRepository.findById(recordingId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> recordingService.undelete(recordingId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Recording: " + recordingId);

        verify(recordingRepository, times(1)).findById(recordingId);
        verify(captureSessionService, never()).undelete(captureSession.getId());
        verify(recordingRepository, never()).save(any());
    }

    @DisplayName("Should throw error when capture session cannot be found")
    @Test
    void undeleteCaptureSessionNotFound() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setCaptureSession(captureSession);

        when(recordingRepository.findById(recording.getId())).thenReturn(Optional.of(recording));

        doThrow(new NotFoundException("Capture Session: " + captureSession.getId()))
            .when(captureSessionService)
            .undelete(captureSession.getId());

        var message = assertThrows(
            NotFoundException.class,
            () -> recordingService.undelete(recording.getId())
        ).getMessage();
        assertThat(message).isEqualTo("Not found: Capture Session: " + captureSession.getId());

        verify(recordingRepository, times(1)).findById(recording.getId());
        verify(captureSessionService, times(1)).undelete(captureSession.getId());
        verify(recordingRepository, never()).save(recording);
    }

    @Test
    @DisplayName("Should return the next version number for recordings")
    void getNextVersionNumberSuccess() {
        var id = UUID.randomUUID();
        when(recordingRepository.countByParentRecording_Id(id))
            .thenReturn(0);

        assertThat(recordingService.getNextVersionNumber(id)).isEqualTo(2);
    }

    @Test
    @DisplayName("Sync recording with storage when filename and duration has changed")
    void syncRecordingWithStorageFilenameDurationChanged() {
        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setDuration(Duration.ofMinutes(3));

        when(recordingRepository.findById(recording.getId())).thenReturn(Optional.of(recording));
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString()))
            .thenReturn("updated.mp4");
        when(azureFinalStorageService.getRecordingDuration(recording.getId()))
            .thenReturn(Duration.ofMinutes(30));

        recordingService.syncRecordingMetadataWithStorage(recording.getId());

        verify(recordingRepository, times(1)).findById(recording.getId());
        verify(azureFinalStorageService, times(1)).getMp4FileName(recording.getId().toString());
        verify(azureFinalStorageService, times(1)).getRecordingDuration(recording.getId());
        verify(recordingRepository, times(1)).saveAndFlush(any(Recording.class));
    }

    @Test
    @DisplayName("Sync recording with storage when filename and duration not has changed")
    void syncRecordingWithStorageNotChanged() {
        Recording recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setFilename("filename.mp4");
        recording.setDuration(Duration.ofMinutes(3));

        when(recordingRepository.findById(recording.getId())).thenReturn(Optional.of(recording));
        when(azureFinalStorageService.getMp4FileName(recording.getId().toString()))
            .thenReturn("filename.mp4");
        when(azureFinalStorageService.getRecordingDuration(recording.getId()))
            .thenReturn(Duration.ofMinutes(3));

        recordingService.syncRecordingMetadataWithStorage(recording.getId());

        verify(recordingRepository, times(1)).findById(recording.getId());
        verify(azureFinalStorageService, times(1)).getMp4FileName(recording.getId().toString());
        verify(azureFinalStorageService, times(1)).getRecordingDuration(recording.getId());
        verify(recordingRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Find all recordings with null duration")
    void findAllDurationNullSuccess() {
        recordingEntity.setDuration(null);

        when(recordingRepository.findAllByDurationIsNullAndDeletedAtIsNull())
            .thenReturn(List.of(recordingEntity));

        List<RecordingDTO> results = recordingService.findAllDurationNull();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getId()).isEqualTo(recordingEntity.getId());

        verify(recordingRepository, times(1)).findAllByDurationIsNullAndDeletedAtIsNull();
    }

    @Test
    @DisplayName("ForceUpsert - Create a recording when it does not exist")
    void forceUpsertCreateSuccess() {
        var captureSession1 = new CaptureSession();
        captureSession1.setId(UUID.randomUUID());
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(captureSession1.getId());
        recordingModel.setVersion(1);
        recordingModel.setFilename("test-creation.mp4");

        when(recordingRepository.findById(recordingModel.getId())).thenReturn(Optional.empty());
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession1.getId()))
            .thenReturn(Optional.of(captureSession1));
        when(recordingRepository.save(any(Recording.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(recordingService.forceUpsert(recordingModel)).isEqualTo(UpsertResult.CREATED);
        verify(recordingRepository, times(1)).save(any(Recording.class));
    }

    @Test
    @DisplayName("ForceUpsert - Update a recording when it already exists")
    void forceUpsertUpdateSuccess() {
        var captureSession1 = new CaptureSession();
        captureSession1.setId(UUID.randomUUID());
        var existingRecording = new Recording();
        existingRecording.setId(UUID.randomUUID());
        existingRecording.setVersion(1);

        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(existingRecording.getId());
        recordingModel.setCaptureSessionId(captureSession1.getId());
        recordingModel.setVersion(2);
        recordingModel.setFilename("test-update.mp4");

        when(recordingRepository.findById(recordingModel.getId()))
            .thenReturn(Optional.of(existingRecording));
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession1.getId()))
            .thenReturn(Optional.of(captureSession1));
        when(recordingRepository.save(any(Recording.class))).thenAnswer(i -> i.getArgument(0));

        assertThat(recordingService.forceUpsert(recordingModel)).isEqualTo(UpsertResult.UPDATED);
        verify(recordingRepository, times(1)).save(any(Recording.class));
    }

    @Test
    @DisplayName("ForceUpsert - Fail when parent recording is not found")
    void forceUpsertParentRecordingNotFound() {
        var captureSession1 = new CaptureSession();
        captureSession1.setId(UUID.randomUUID());
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(captureSession1.getId());
        recordingModel.setParentRecordingId(UUID.randomUUID());
        recordingModel.setVersion(1);

        when(recordingRepository.findById(recordingModel.getId())).thenReturn(Optional.empty());
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession1.getId()))
            .thenReturn(Optional.of(captureSession1));
        when(recordingRepository.findById(recordingModel.getParentRecordingId()))
            .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> recordingService.forceUpsert(recordingModel));
        verify(recordingRepository, never()).save(any(Recording.class));
    }

    @Test
    @DisplayName("ForceUpsert - Fail when capture session is not found")
    void forceUpsertCaptureSessionNotFound() {
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setCaptureSessionId(UUID.randomUUID());

        when(recordingRepository.findById(recordingModel.getId())).thenReturn(Optional.empty());
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(recordingModel.getCaptureSessionId()))
            .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> recordingService.forceUpsert(recordingModel));
        verify(recordingRepository, never()).save(any(Recording.class));
    }
}
