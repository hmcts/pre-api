package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;
    private final CaseRepository caseRepository;
    private final ShareBookingRepository shareBookingRepository;
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;

    @Autowired
    public ReportService(CaptureSessionRepository captureSessionRepository,
                         RecordingRepository recordingRepository,
                         CaseRepository caseRepository,
                         ShareBookingRepository shareBookingRepository,
                         AuditRepository auditRepository,
                         UserRepository userRepository) {
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.caseRepository = caseRepository;
        this.shareBookingRepository = shareBookingRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public List<CaptureSessionReportDTO> reportCaptureSessions() {
        return captureSessionRepository
            .findAll()
            .stream()
            .map(c ->
                recordingRepository
                    .findByCaptureSessionAndDeletedAtIsNullAndVersionOrderByCreatedAt(c, 1)
                    .map(CaptureSessionReportDTO::new)
                    .orElse(new CaptureSessionReportDTO(c))
            ).collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingsPerCaseReportDTO> reportRecordingsPerCase() {
        return caseRepository
            .findAll()
            .stream()
            .map(c -> new RecordingsPerCaseReportDTO(
                c,
                captureSessionRepository.countAllByBooking_CaseId_IdAndStatus(c.getId(), RecordingStatus.AVAILABLE)
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
    public List<SharedReportDTO> reportShared() {
        return shareBookingRepository
            .findAll()
            .stream()
            .map(SharedReportDTO::new)
            .sorted(Comparator.comparing(SharedReportDTO::getSharedAt))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<ScheduleReportDTO> reportScheduled() {
        return captureSessionRepository
            .findAllByStatus(RecordingStatus.AVAILABLE)
            .stream()
            .map(ScheduleReportDTO::new)
            .sorted(Comparator.comparing(ScheduleReportDTO::getScheduledFor))
            .collect(Collectors.toList());
    }

    @Transactional
    public List<PlaybackReportDTO> reportPlayback(AuditLogSource source) {
        if (source == AuditLogSource.PORTAL || source == AuditLogSource.APPLICATION) {
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
                .map(a ->
                         new PlaybackReportDTO(
                             a,
                             userRepository
                                 .findById(a.getCreatedBy())
                                 .map(User::getEmail)
                                 .orElse(null),
                             recordingRepository
                                 .findById(a.getTableRecordId())
                                 .orElse(null))
                )
                .collect(Collectors.toList());
        } else {
            throw new NotFoundException("Report for playback source: " + source);
        }
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
}
