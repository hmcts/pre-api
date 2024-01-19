package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;

import java.util.List;
import java.util.UUID;

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

    @BeforeAll
    static void setUp() {
        booking = new Booking();
        booking.setId(UUID.randomUUID());
        captureSession = new CaptureSession();
        captureSession.setId(UUID.randomUUID());
        captureSession.setBooking(booking);
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
