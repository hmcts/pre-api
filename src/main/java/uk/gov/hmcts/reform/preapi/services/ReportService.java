package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportArgsRecord;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingParticipantsReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.UserPrimaryCourtReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.UserRecordingPlaybackReportDTOV2;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
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
    private final ShareBookingRepository shareBookingRepository;
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AppAccessRepository appAccessRepository;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public ReportService(CaptureSessionRepository captureSessionRepository,
                         RecordingRepository recordingRepository,
                         ShareBookingRepository shareBookingRepository,
                         AuditRepository auditRepository,
                         UserRepository userRepository,
                         AppAccessRepository appAccessRepository,
                         PortalAccessRepository portalAccessRepository) {
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.shareBookingRepository = shareBookingRepository;
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.appAccessRepository = appAccessRepository;
        this.portalAccessRepository = portalAccessRepository;
    }

    @Transactional
    public List<ConcurrentCaptureSessionReportDTOV2> reportCaptureSessions() {
        return captureSessionRepository
            .reportConcurrentCaptureSessions()
            .stream()
            .map(ConcurrentCaptureSessionReportDTOV2::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingsPerCaseReportDTOV2> reportRecordingsPerCase() {
        return recordingRepository
            .countRecordingsPerCase()
            .stream()
            .map(data -> new RecordingsPerCaseReportDTOV2((Case) data[0], ((Long) data[1]).intValue()))
            .toList();
    }

    @Transactional
    public List<EditReportDTOV2> reportEdits() {
        return recordingRepository
            .findAllByParentRecordingIsNotNull()
            .stream()
            .sorted(Comparator.comparing(Recording::getCreatedAt))
            .map(EditReportDTOV2::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<SharedReportDTOV2> reportShared(
        UUID courtId,
        UUID bookingId,
        UUID sharedWithId,
        String sharedWithEmail,
        boolean onlyActive
    ) {
        return shareBookingRepository
            .searchAll(courtId, bookingId, sharedWithId, sharedWithEmail, onlyActive)
            .stream()
            .sorted(Comparator.comparing(ShareBooking::getCreatedAt))
            .map(SharedReportDTOV2::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<ScheduleReportDTOV2> reportScheduled() {
        return captureSessionRepository
            .findAllByStatus(RecordingStatus.RECORDING_AVAILABLE)
            .stream()
            .sorted(Comparator.comparing(c -> c.getBooking().getScheduledFor()))
            .map(ScheduleReportDTOV2::new)
            .collect(Collectors.toList());
    }

    public List<UserRecordingPlaybackReportDTOV2> userRecordingPlaybackReport() {
        return auditRepository
            .findAllAccessAttempts()
            .stream()
            .map(a -> {
                var args = toPlaybackReport(a);
                return new UserRecordingPlaybackReportDTOV2(args.audit(), args.user(), args.recording());
            })
            .toList();
    }

    @Transactional
    public List<PlaybackReportDTOV2> reportPlayback(AuditLogSource source) {
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
                .map(a -> {
                    var args = toPlaybackReport(a);
                    return new PlaybackReportDTOV2(args.audit(), args.user(), args.recording());
                })
                .toList();
        } else {
            throw new NotFoundException("Report for playback source: " + source);
        }
    }

    @Transactional
    public List<CompletedCaptureSessionReportDTOV2> reportCompletedCaptureSessions() {
        return recordingRepository
            .findAllCompletedCaptureSessionsWithRecordings()
            .stream()
            .sorted(Comparator.comparing(r -> r.getCaptureSession().getBooking().getScheduledFor()))
            .map(CompletedCaptureSessionReportDTOV2::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<AccessRemovedReportDTOV2> reportAccessRemoved() {
        return shareBookingRepository
            .findAllByDeletedAtIsNotNull()
            .stream()
            .sorted(Comparator.comparing(ShareBooking::getDeletedAt))
            .map(AccessRemovedReportDTOV2::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingParticipantsReportDTO> reportRecordingParticipants() {
        return recordingRepository
            .findAllByParentRecordingIsNull()
            .stream()
            .map(this::getParticipantsForRecording)
            .flatMap(List::stream)
            .toList();
    }

    private List<RecordingParticipantsReportDTO> getParticipantsForRecording(Recording recording) {
        return recording
            .getCaptureSession()
            .getBooking()
            .getParticipants()
            .stream()
            .map(participant -> new RecordingParticipantsReportDTO(participant, recording))
            .toList();
    }

    private PlaybackReportArgsRecord toPlaybackReport(Audit audit) {
        var auditDetails = audit.getAuditDetails() != null && !audit.getAuditDetails().isNull();
        UUID recordingId = null;
        if (auditDetails) {
            if (audit.getAuditDetails().hasNonNull("recordingId")) {
                recordingId = UUID.fromString(audit.getAuditDetails().get("recordingId").asText());
            } else if (audit.getAuditDetails().hasNonNull("recordinguid")) {
                recordingId = UUID.fromString(audit.getAuditDetails().get("recordinguid").asText());
            }
        }

        var user = audit.getCreatedBy() != null
            ? userRepository.findById(audit.getCreatedBy())
                            .orElse(appAccessRepository.findById(audit.getCreatedBy())
                                                       .map(AppAccess::getUser)
                                                       .orElse(portalAccessRepository.findById(audit.getCreatedBy())
                                                                                     .map(PortalAccess::getUser)
                                                                                     .orElse(null)))
            : null;

        var recording = recordingId != null
            ? recordingRepository.findById(recordingId).orElse(null)
            : null;

        return new PlaybackReportArgsRecord(audit, user, recording);
    }

    @Transactional
    public List<UserPrimaryCourtReportDTO> reportUserPrimaryCourts() {

        return this.appAccessRepository.getUserPrimaryCourtsForReport()
            .stream()
            .map(access -> new UserPrimaryCourtReportDTO(access.getUser().getFirstName(),
                                                         access.getUser().getLastName(),
                                                         access.getCourt().getName(),
                                                         access.isActive(),
                                                         access.getRole().getName(),
                                                         access.getLastAccess()))
            .toList();
    }
}
