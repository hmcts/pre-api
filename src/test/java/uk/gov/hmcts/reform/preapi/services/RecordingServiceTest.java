package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
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
@SuppressWarnings("PMD.LawOfDemeter")
class RecordingServiceTest {
    private static Recording recordingEntity;

    private static Booking bookingEntity;

    @MockBean
    private RecordingRepository recordingRepository;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @Autowired
    private RecordingService recordingService;

    @BeforeAll
    static void setUp() {
        recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());
        bookingEntity = new Booking();
        bookingEntity.setId(UUID.randomUUID());
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(bookingEntity);
        recordingEntity.setCaptureSession(captureSession);
        recordingEntity.setVersion(1);
        recordingEntity.setUrl("http://localhost");
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));
    }

    @BeforeEach
    void resetDelete() {
        recordingEntity.setDeletedAt(null);
    }

    @DisplayName("Find a recording by it's id and related booking id and return a model")
    @Test
    void findRecordingByIdSuccess() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        var model = recordingService.findById(bookingEntity.getId(), recordingEntity.getId());
        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a recording by it's id which is missing")
    @Test
    void findRecordingByIdMissing() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.empty());

        var model = recordingService.findById(bookingEntity.getId(), recordingEntity.getId());
        assertThat(model).isNull();
    }

    @DisplayName("Find a recording by it's id when the booking id is missing")
    @Test
    void findRecordingByIdBookingIdMissing() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> recordingService.findById(bookingEntity.getId(), recordingEntity.getId())
        );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(bookingEntity.getId());
        verify(recordingRepository, never())
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(any(), any());
    }

    @DisplayName("Find a list of recordings by it's related booking id and return a list of models")
    @Test
    void findAllRecordingsSuccess() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(List.of(recordingEntity));

        var modelList = recordingService.findAllByBookingId(bookingEntity.getId());
        assertThat(modelList.size()).isEqualTo(1);
        assertThat(modelList.get(0).getId()).isEqualTo(recordingEntity.getId());
        assertThat(modelList.get(0).getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a list of recordings by it's related booking id when booking does not exist")
    @Test
    void findAllRecordingsBookingNotFound() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> recordingService.findAllByBookingId(bookingEntity.getId())
        );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(bookingEntity.getId());
        verify(recordingRepository, never()).findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(any());
    }

    @DisplayName("Create a recording")
    @Test
    void createRecordingSuccess() {
        var captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        var recordingModel = new RecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(captureSession.getId());
        recordingModel.setVersion(1);

        var recordingEntity = new Recording();
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
                captureSession.getId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.of(captureSession));
        when(recordingRepository.findById(any())).thenReturn(Optional.empty());
        when(recordingRepository.save(recordingEntity)).thenReturn(recordingEntity);

        assertThat(recordingService.upsert(bookingEntity.getId(), recordingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Update a recording")
    @Test
    void updateRecordingSuccess() {
        var recordingModel = new RecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(2);
        var recordingEntity = new Recording();

        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(true);

        when(recordingRepository.findById(any())).thenReturn(Optional.empty());
        when(recordingRepository.save(recordingEntity)).thenReturn(recordingEntity);

        assertThat(recordingService.upsert(bookingEntity.getId(), recordingModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Fail to create recording - Booking not found")
    @Test
    void createRecordingFailBookingNotFound() {
        var recordingModel = new RecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);

        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(NotFoundException.class, () -> {
            recordingService.upsert(bookingEntity.getId(), recordingModel);
        });
    }

    @DisplayName("Fail to create recording - CaptureSession not found")
    @Test
    void createRecordingFailCaptureSessionNotFound() {
        var recordingModel = new RecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setCaptureSessionId(UUID.randomUUID());

        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            recordingService.upsert(bookingEntity.getId(), recordingModel);
        });
    }

    @DisplayName("Fail to create recording - Parent Recording not found")
    @Test
    void createRecordingFailParentRecordingNotFound() {
        var recordingModel = new RecordingDTO();
        recordingModel.setId(UUID.randomUUID());
        recordingModel.setVersion(1);
        recordingModel.setParentRecordingId(UUID.randomUUID());
        recordingModel.setCaptureSessionId(UUID.randomUUID());


        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.existsByIdAndDeletedAtIsNull(recordingModel.getId())
        ).thenReturn(false);
        when(
            captureSessionRepository.findByIdAndBooking_IdAndDeletedAtIsNull(
                recordingModel.getCaptureSessionId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.of(new CaptureSession()));
        when(
            recordingRepository.findById(recordingModel.getParentRecordingId())
        ).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> {
            recordingService.upsert(bookingEntity.getId(), recordingModel);
        });
    }


    @DisplayName("Find a list of recordings by it's related booking id when recording has been deleted")
    @Test
    void findAllRecordingsDeleted() {
        recordingEntity.setDeletedAt(Timestamp.from(Instant.now()));
        recordingRepository.save(recordingEntity);
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);

        var models = recordingService.findAllByBookingId(bookingEntity.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(bookingEntity.getId());
        verify(recordingRepository, times(1))
            .findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(bookingEntity.getId());

        assertThat(models.size()).isEqualTo(0);
    }

    @DisplayName("Delete a recording by it's id and related booking id")
    @Test
    void deleteRecordingSuccess() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        recordingService.deleteById(bookingEntity.getId(), recordingEntity.getId());

        verify(recordingRepository, times(1))
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            );
        verify(recordingRepository, times(1)).save(recordingEntity);

        assertThat(recordingEntity.isDeleted()).isTrue();
    }

    @DisplayName("Delete a recording by it's id and related booking id when recording doesn't exist")
    @Test
    void deleteRecordingNotFound() {
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository
                .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                    recordingEntity.getId(),
                    bookingEntity.getId()
                )
        ).thenReturn(Optional.empty());

        assertThrows(
            NotFoundException.class,
            () -> recordingService.deleteById(bookingEntity.getId(), recordingEntity.getId())
        );

        verify(recordingRepository, times(1))
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            );
        verify(recordingRepository, never()).save(recordingEntity);
    }

    @DisplayName("Delete a recording by it's id and related booking id when recording has already been deleted")
    @Test
    void deleteRecordingAlreadyDeleted() {
        Timestamp now = Timestamp.from(Instant.now());
        recordingEntity.setDeletedAt(now);
        when(
            bookingRepository.existsByIdAndDeletedAtIsNull(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            )
        ).thenReturn(Optional.of(recordingEntity));

        assertThrows(
            NotFoundException.class,
            () -> recordingService.deleteById(bookingEntity.getId(), recordingEntity.getId())
        );

        verify(recordingRepository, times(1))
            .findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
                recordingEntity.getId(),
                bookingEntity.getId()
            );
        verify(recordingRepository, never()).save(recordingEntity);

        assertThat(recordingEntity.isDeleted()).isTrue();
        assertThat(recordingEntity.getDeletedAt()).isEqualTo(now);
    }

    @DisplayName("Delete a recording by it's id and related booking id when booking doesn't exist")
    @Test
    void deleteRecordingBookingNotFound() {
        when(
            bookingRepository.existsById(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> recordingService.deleteById(bookingEntity.getId(), recordingEntity.getId())
        );

        verify(recordingRepository, never()).findByIdAndCaptureSession_Booking_IdAndDeletedAtIsNullAndCaptureSessionDeletedAtIsNull(
            any(),
            any()
        );
        verify(recordingRepository, never()).save(recordingEntity);
    }
}
