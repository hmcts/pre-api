package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
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

    @DisplayName("Find a recording by it's id and related booking id and return a model")
    @Test
    void findRecordingByIdSuccess() {
        when(
            bookingRepository.existsById(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_Id(recordingEntity.getId(), bookingEntity.getId())
        ).thenReturn(Optional.of(recordingEntity));

        var model = recordingService.findById(bookingEntity.getId(), recordingEntity.getId());
        assertThat(model.getId()).isEqualTo(recordingEntity.getId());
        assertThat(model.getCaptureSessionId()).isEqualTo(recordingEntity.getCaptureSession().getId());
    }

    @DisplayName("Find a recording by it's id which is missing")
    @Test
    void findRecordingByIdMissing() {
        when(
            bookingRepository.existsById(bookingEntity.getId())
        ).thenReturn(true);
        when(
            recordingRepository.findByIdAndCaptureSession_Booking_Id(recordingEntity.getId(), bookingEntity.getId())
        ).thenReturn(Optional.empty());

        var model = recordingService.findById(bookingEntity.getId(), recordingEntity.getId());
        assertThat(model).isNull();
    }

    @DisplayName("Find a recording by it's id when the booking id is missing")
    @Test
    void findRecordingByIdBookingIdMissing() {
        when(
            bookingRepository.existsById(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> recordingService.findById(bookingEntity.getId(), recordingEntity.getId())
        );

        verify(bookingRepository, times(1)).existsById(bookingEntity.getId());
        verify(recordingRepository, never()).findByIdAndCaptureSession_Booking_Id(any(), any());
    }

    @DisplayName("Find a list of recordings by it's related booking id and return a list of models")
    @Test
    void findAllRecordingsSuccess() {
        when(
            bookingRepository.existsById(bookingEntity.getId())
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
            bookingRepository.existsById(bookingEntity.getId())
        ).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> recordingService.findAllByBookingId(bookingEntity.getId())
        );

        verify(bookingRepository, times(1)).existsById(bookingEntity.getId());
        verify(recordingRepository, never()).findAllByCaptureSession_Booking_IdAndDeletedAtIsNull(any());
    }
}
