package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @MockBean
    private RecordingRepository recordingRepository;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

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
        recordingEntity.setUrl("http://localhost");
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
            recordingRepository.searchAllBy(eq(params), any())
        ).thenReturn(new PageImpl<>(List.of(recordingEntity)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = recordingService.findAll(params, null).get().toList();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(recordingEntity.getId());
        assertThat(modelList.getFirst().getCaptureSession().getId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a list of recordings filtered by scheduledFor and return a list of models")
    @Test
    void findAllRecordingsScheduledForSuccess() {
        var params = new SearchRecordings();
        params.setScheduledForFrom(Timestamp.valueOf("2023-01-01 00:00:00"));
        params.setScheduledForUntil(Timestamp.valueOf("2023-01-01 23:59:59"));

        when(
            recordingRepository.searchAllBy(params, null)
        ).thenReturn(new PageImpl<>(List.of(recordingEntity)));
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var modelList = recordingService.findAll(params, null).get().toList();
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.getFirst().getId()).isEqualTo(recordingEntity.getId());
        assertThat(modelList.getFirst().getCaptureSession().getId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Create a recording")
    @Test
    void createRecordingSuccess() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
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
        var recordingModel = new CreateRecordingDTO();
        recordingModel.setId(recordingEntity.getId());
        recordingModel.setCaptureSessionId(UUID.randomUUID());
        recordingModel.setVersion(2);
        when(
            recordingRepository.findById(recordingModel.getId())
        ).thenReturn(Optional.of(recordingEntity));
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(recordingModel.getCaptureSessionId())).thenReturn(Optional.of(new CaptureSession()));

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

    @DisplayName("Fail to create recording - Parent Recording not found")
    @Test
    void createRecordingFailParentRecordingNotFound() {
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
        ).thenReturn(Optional.of(new CaptureSession()));
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
        when(recordingRepository.searchAllBy(params, null)).thenReturn(Page.empty());
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var models = recordingService.findAll(params, null).get().toList();

        verify(recordingRepository, times(1))
            .searchAllBy(params, null);

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
        verify(recordingRepository, times(1)).deleteById(recordingEntity.getId());
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

    @DisplayName("Should delete all recordings by capture session")
    @Test
    void deleteCascadeSuccess() {
        recordingService.deleteCascade(recordingEntity.getCaptureSession());

        verify(recordingRepository, times(1)).deleteAllByCaptureSession(recordingEntity.getCaptureSession());
    }

    @DisplayName("Should set scheduled for from and until when scheduled for is set")
    @Test
    void searchRecordingsScheduledForFromUntilSet() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var params = new SearchRecordings();
        var scheduledFor = new Date();
        params.setScheduledFor(scheduledFor);
        when(recordingRepository.searchAllBy(params, null)).thenReturn(Page.empty());

        recordingService.findAll(params, null);

        assertThat(params.getScheduledForFrom()).isNotNull();
        assertThat(params.getScheduledForFrom().toInstant()).isEqualTo(scheduledFor.toInstant());

        assertThat(params.getScheduledForUntil()).isNotNull();
        assertThat(params.getScheduledForUntil().toInstant())
            .isEqualTo(scheduledFor.toInstant().plus(86399, ChronoUnit.SECONDS));
    }

    @DisplayName("Should not set scheduled for from and until when scheduled for is not set")
    @Test
    void searchRecordingsScheduledForFromUntilNotSet() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var params = new SearchRecordings();
        when(recordingRepository.searchAllBy(params, null)).thenReturn(Page.empty());

        recordingService.findAll(params, null);

        assertThat(params.getScheduledForFrom()).isNull();

        assertThat(params.getScheduledForUntil()).isNull();
    }
}
