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
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = RecordingService.class)
@SuppressWarnings({ "PMD.LawOfDemeter", "checkstyle:LineLength"})
class RecordingServiceTest {
    private static Recording recordingEntity;

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

        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(bookingEntity);
        var user = new uk.gov.hmcts.reform.preapi.entities.User();
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
        when(
            recordingRepository.searchAllBy(any(), any(), any(), any())
        ).thenReturn(new PageImpl<>(List.of(recordingEntity)));

        var modelList = recordingService.findAll(null, null, null, null).get().toList();
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
        recordingModel.setVersion(2);
        when(
            recordingRepository.findById(recordingModel.getId())
        ).thenReturn(Optional.of(recordingEntity));

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

        when(recordingRepository.searchAllBy(null, null, null, null)).thenReturn(Page.empty());

        var models = recordingService.findAll(null, null, null,null).get().toList();

        verify(recordingRepository, times(1))
            .searchAllBy(null, null, null, null);

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
}
