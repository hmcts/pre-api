package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CaptureSessionService.class)
public class CaptureSessionServiceTest {
    @MockBean
    private RecordingService recordingService;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @Autowired
    private CaptureSessionService captureSessionService;

    private static CaptureSession captureSession;

    private static Booking booking;

    private static User user;

    @BeforeAll
    static void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        booking = new Booking();
        booking.setId(UUID.randomUUID());
        captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setOrigin(RecordingOrigin.PRE);
        captureSession.setBooking(booking);
        captureSession.setIngestAddress("example ingest address");
        captureSession.setLiveOutputUrl("example url");
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSession.setStartedByUser(user);
        captureSession.setFinishedAt(Timestamp.from(Instant.now()));
        captureSession.setFinishedByUser(user);
        captureSession.setStatus(RecordingStatus.AVAILABLE);
    }

    @DisplayName("Find a capture session and return a model")
    @Test
    void findByIdSuccess() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.of(captureSession));

        var model = captureSessionService.findById(captureSession.getId());

        assertThat(model.getId()).isEqualTo(captureSession.getId());
        assertThat(model.getBookingId()).isEqualTo(booking.getId());
        assertThat(model.getOrigin()).isEqualTo(captureSession.getOrigin());
        assertThat(model.getIngestAddress()).isEqualTo(captureSession.getIngestAddress());
        assertThat(model.getLiveOutputUrl()).isEqualTo(captureSession.getLiveOutputUrl());
        assertThat(model.getStartedAt()).isEqualTo(captureSession.getStartedAt());
        assertThat(model.getStartedByUserId()).isEqualTo(user.getId());
        assertThat(model.getFinishedAt()).isEqualTo(captureSession.getFinishedAt());
        assertThat(model.getFinishedByUserId()).isEqualTo(user.getId());
        assertThat(model.getStatus()).isEqualTo(captureSession.getStatus());
        assertThat(model.getDeletedAt()).isEqualTo(captureSession.getDeletedAt());
    }

    @DisplayName("Find a capture session when capture session does not exist")
    @Test
    void findByIdNotFound() {
        when(captureSessionRepository.findByIdAndDeletedAtIsNull(captureSession.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> captureSessionService.findById(captureSession.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: CaptureSession: " + captureSession.getId());
    }

    @DisplayName("Should delete all attached recordings before marking capture session as deleted")
    @Test
    void deleteCascadeSuccess() {
        when(captureSessionRepository.findAllByBookingAndDeletedAtIsNull(booking)).thenReturn(List.of(captureSession));

        captureSessionService.deleteCascade(booking);

        verify(captureSessionRepository, times(1)).findAllByBookingAndDeletedAtIsNull(booking);
        verify(recordingService, times(1)).deleteCascade(captureSession);
        verify(captureSessionRepository, times(1)).deleteAllByBooking(booking);
    }
}
