package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ReportService.class)
public class ReportServiceTest {
    private static Recording recordingEntity;
    private static CaptureSession captureSessionEntity;
    private static Court courtEntity;
    private static Region regionEntity;
    private static Case caseEntity;

    @MockBean
    private CaptureSessionRepository captureSessionRepository;

    @MockBean
    private RecordingRepository recordingRepository;

    @Autowired
    private ReportService reportService;

    @BeforeAll
    static void setUp() {
        regionEntity = new Region();
        regionEntity.setId(UUID.randomUUID());
        regionEntity.setName("London");

        courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        courtEntity.setName("Example Court");
        courtEntity.setRegions(Set.of(regionEntity));

        recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());

        caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(courtEntity);
        caseEntity.setReference("ABC123");

        Booking bookingEntity = new Booking();
        bookingEntity.setId(UUID.randomUUID());
        bookingEntity.setCaseId(caseEntity);

        captureSessionEntity = new CaptureSession();
        captureSessionEntity.setId(UUID.randomUUID());
        captureSessionEntity.setBooking(bookingEntity);

        recordingEntity.setCaptureSession(captureSessionEntity);
        recordingEntity.setVersion(1);
        recordingEntity.setUrl("http://localhost");
        recordingEntity.setFilename("example-filename.txt");
        recordingEntity.setCreatedAt(Timestamp.from(Instant.now()));
    }


    @BeforeEach
    void reset() {
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setStatus(null);
        recordingEntity.setDuration(null);
    }

    @DisplayName("Find all capture sessions and return a list of models as a report when capture session is incomplete")
    @Test
    void captureSessionReportCaptureSessionIncompleteSuccess() {
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        when(captureSessionRepository.findAll()).thenReturn(List.of(captureSessionEntity));
        when(recordingRepository
                 .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(
                     captureSessionEntity,
                     1
                 )
        ).thenReturn(Optional.empty());

        var report = reportService.reportCaptureSessions();

        verify(captureSessionRepository, times(1)).findAll();
        verify(recordingRepository, times(1))
            .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(any(), eq(1));

        assertThat(report.size()).isEqualTo(1);
        var first = report.getFirst();

        assertThat(first.getId()).isEqualTo(captureSessionEntity.getId());
        assertThat(first.getStartTime()).isEqualTo(captureSessionEntity.getStartedAt());
        assertThat(first.getEndTime()).isEqualTo(captureSessionEntity.getFinishedAt());
        assertThat(first.getDuration()).isNull();
        assertThat(first.getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(first.getCourt()).isEqualTo(courtEntity.getName());
        assertThat(first.getRegion().stream().findFirst().isPresent()).isTrue();
        assertThat(first.getRegion().stream().findFirst().get().getName()).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find all capture sessions and return a list of models as a report when capture session is complete")
    @Test
    void captureSessionReportCaptureSessionCompleteSuccess() {
        recordingEntity.setDuration(Duration.ofMinutes(3));
        captureSessionEntity.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionEntity.setFinishedAt(Timestamp.from(Instant.now()));
        when(captureSessionRepository.findAll()).thenReturn(List.of(captureSessionEntity));
        when(recordingRepository
                 .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(
                     captureSessionEntity,
                     1
                 )
        ).thenReturn(Optional.of(recordingEntity));

        var report = reportService.reportCaptureSessions();

        verify(captureSessionRepository, times(1)).findAll();
        verify(recordingRepository, times(1))
            .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(any(), eq(1));

        assertThat(report.size()).isEqualTo(1);
        var first = report.getFirst();

        assertThat(first.getId()).isEqualTo(captureSessionEntity.getId());
        assertThat(first.getStartTime()).isEqualTo(captureSessionEntity.getStartedAt());
        assertThat(first.getEndTime()).isEqualTo(captureSessionEntity.getFinishedAt());
        assertThat(first.getDuration()).isEqualTo(recordingEntity.getDuration());
        assertThat(first.getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(first.getCourt()).isEqualTo(courtEntity.getName());
        assertThat(first.getRegion().stream().findFirst().isPresent()).isTrue();
        assertThat(first.getRegion().stream().findFirst().get().getName()).isEqualTo(regionEntity.getName());
    }

    @DisplayName("Find all capture sessions with recording available and get report on booking details")
    @Test
    void reportScheduledSuccess() {
        var userEntity = new User();
        userEntity.setId(UUID.randomUUID());
        userEntity.setEmail("example@example.com");
        captureSessionEntity.setStatus(RecordingStatus.AVAILABLE);
        captureSessionEntity.setStartedByUser(userEntity);

        var otherBooking = new Booking();
        otherBooking.setId(UUID.randomUUID());
        otherBooking.setCaseId(caseEntity);
        otherBooking.setScheduledFor(Timestamp.from(Instant.MIN));

        captureSessionEntity.getBooking().setScheduledFor(Timestamp.from(Instant.MAX));

        var otherCaptureSessionEntity = new CaptureSession();
        otherCaptureSessionEntity.setId(UUID.randomUUID());
        otherCaptureSessionEntity.setBooking(otherBooking);
        otherCaptureSessionEntity.setStatus(RecordingStatus.AVAILABLE);
        otherCaptureSessionEntity.setStartedByUser(userEntity);

        when(captureSessionRepository.findAllByStatus(RecordingStatus.AVAILABLE))
            .thenReturn(List.of(otherCaptureSessionEntity, captureSessionEntity));

        var report = reportService.reportScheduled();

        assertThat(report.size()).isEqualTo(2);
        assertThat(report.getFirst().getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.getFirst().getScheduledFor()).isEqualTo(captureSessionEntity.getBooking().getScheduledFor());
        assertThat(report.getFirst().getCaptureSessionUser()).isEqualTo(userEntity.getEmail());

        assertThat(report.get(1).getCaseReference()).isEqualTo(caseEntity.getReference());
        assertThat(report.get(1).getScheduledFor()).isEqualTo(otherBooking.getScheduledFor());
    }
}
