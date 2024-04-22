package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;
    private final CaseRepository caseRepository;
    private final ShareBookingRepository shareBookingRepository;
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AppAccessRepository appAccessRepository;

    @Autowired
    public ReportService(CaptureSessionRepository captureSessionRepository,
                         RecordingRepository recordingRepository,
                         CaseRepository caseRepository,
                         ShareBookingRepository shareBookingRepository,
                         AuditRepository auditRepository,
                         UserRepository userRepository,
                         AppAccessRepository appAccessRepository) {
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.caseRepository = caseRepository;
        this.shareBookingRepository = shareBookingRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.appAccessRepository = appAccessRepository;
    }

    @Transactional
    public List<ConcurrentCaptureSessionReportDTO> reportCaptureSessions() {
        return captureSessionRepository
            .findAll()
            .stream()
            .map(c -> {
                var recordings = recordingRepository
                    .findAllByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(c, 1);
                return (recordings.isEmpty())
                    ? new ConcurrentCaptureSessionReportDTO(c)
                    : new ConcurrentCaptureSessionReportDTO(recordings.getFirst());
            })
            .collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingsPerCaseReportDTO> reportRecordingsPerCase() {
        return caseRepository
            .findAll()
            .stream()
            .map(c -> new RecordingsPerCaseReportDTO(
                c,
                captureSessionRepository.countAllByBooking_CaseId_IdAndStatus(
                    c.getId(),
                    RecordingStatus.RECORDING_AVAILABLE
                )
            ))
            .sorted((case1, case2) -> Integer.compare(case2.getCount(), case1.getCount()))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<EditReportDTO> reportEdits() {
        return recordingRepository
            .findAllByParentRecordingIsNotNull()
            .stream()
            .map(EditReportDTO::new)
            .sorted(Comparator.comparing(EditReportDTO::getCreatedAt))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<SharedReportDTO> reportShared(
        UUID courtId,
        UUID bookingId,
        UUID sharedWithId,
        String sharedWithEmail
    ) {
        return shareBookingRepository
            .searchAll(courtId, bookingId, sharedWithId, sharedWithEmail)
            .stream()
            .map(SharedReportDTO::new)
            .sorted(Comparator.comparing(SharedReportDTO::getSharedAt))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<ScheduleReportDTO> reportScheduled() {
        return captureSessionRepository
            .findAllByStatus(RecordingStatus.RECORDING_AVAILABLE)
            .stream()
            .map(ScheduleReportDTO::new)
            .sorted(Comparator.comparing(ScheduleReportDTO::getScheduledFor))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<PlaybackReportDTO> reportPlayback(AuditLogSource source) {
        if (source == null) {
            return auditRepository
                .findAllAccessAttempts()
                .stream()
                .map(this::toPlaybackReport)
                .collect(Collectors.toList());
        } else if (source == AuditLogSource.PORTAL || source == AuditLogSource.APPLICATION) {
            final var activityPlay = "Play";
            final var functionalAreaVideoPlayer = "Video Player";
            final var functionalAreaViewRecordings = "View Recordings";

            return auditRepository
                .findBySourceAndFunctionalAreaAndActivity(
                    source,
                    source == AuditLogSource.PORTAL
                        ? functionalAreaVideoPlayer
                        : functionalAreaViewRecordings,
                    activityPlay
                )
                .stream()
                .map(this::toPlaybackReport)
                .collect(Collectors.toList());
        } else {
            throw new NotFoundException("Report for playback source: " + source);
        }
    }

    @Transactional
    public List<CompletedCaptureSessionReportDTO> reportCompletedCaptureSessions() {
        return recordingRepository
            .findAllByParentRecordingIsNull()
            .stream()
            .map(CompletedCaptureSessionReportDTO::new)
            .sorted(Comparator.comparing(CompletedCaptureSessionReportDTO::getScheduledFor))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<AccessRemovedReportDTO> reportAccessRemoved() {
        return shareBookingRepository
            .findAllByDeletedAtIsNotNull()
            .stream()
            .map(AccessRemovedReportDTO::new)
            .sorted(Comparator.comparing(AccessRemovedReportDTO::getRemovedAt))
            .collect(Collectors.toList());
    }

    private PlaybackReportDTO toPlaybackReport(Audit audit) {
        return new PlaybackReportDTO(
            audit,
            audit.getCreatedBy() != null
                ? userRepository
                .findById(audit.getCreatedBy())
                .orElse(appAccessRepository.findById(audit.getCreatedBy())
                            .map(AppAccess::getUser)
                            .orElse(null))
                : null,
            audit.getAuditDetails() != null && audit.getAuditDetails().hasNonNull("recordingId")
                ? recordingRepository
                .findById(UUID.fromString(audit.getAuditDetails().get("recordingId").asText()))
                .orElse(null)
                : null
        );
    }
}
