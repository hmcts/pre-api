package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.CompletedCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.SharedReportDTO;
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
public class LegacyReportService {

    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;
    private final ShareBookingRepository shareBookingRepository;
    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AppAccessRepository appAccessRepository;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public LegacyReportService(CaptureSessionRepository captureSessionRepository,
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
    public List<ConcurrentCaptureSessionReportDTO> reportCaptureSessions() {
        return captureSessionRepository
            .reportConcurrentCaptureSessions()
            .stream()
            .map(ConcurrentCaptureSessionReportDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<RecordingsPerCaseReportDTO> reportRecordingsPerCase() {
        return recordingRepository
            .countRecordingsPerCase()
            .stream()
            .map(data -> new RecordingsPerCaseReportDTO((Case) data[0], ((Long) data[1]).intValue()))
            .toList();
    }

    @Transactional
    public List<EditReportDTO> reportEdits() {
        return recordingRepository
            .findAllByParentRecordingIsNotNull()
            .stream()
            .sorted(Comparator.comparing(Recording::getCreatedAt))
            .map(EditReportDTO::new)
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
            .searchAll(courtId, bookingId, sharedWithId, sharedWithEmail, false)
            .stream()
            .sorted(Comparator.comparing(ShareBooking::getCreatedAt))
            .map(SharedReportDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<ScheduleReportDTO> reportScheduled() {
        return captureSessionRepository
            .findAllByStatus(RecordingStatus.RECORDING_AVAILABLE)
            .stream()
            .sorted(Comparator.comparing(c -> c.getBooking().getScheduledFor()))
            .map(ScheduleReportDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<PlaybackReportDTO> reportPlayback(AuditLogSource source) {
        if (source == null) {
            return auditRepository
                .findAllAccessAttempts()
                .stream()
                .map(this::toPlaybackReport)
                .toList();
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
                .toList();
        } else {
            throw new NotFoundException("Report for playback source: " + source);
        }
    }

    @Transactional
    public List<CompletedCaptureSessionReportDTO> reportCompletedCaptureSessions() {
        return recordingRepository
            .findAllByParentRecordingIsNull()
            .stream()
            .sorted(Comparator.comparing(r -> r.getCaptureSession().getBooking().getScheduledFor()))
            .map(CompletedCaptureSessionReportDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    public List<AccessRemovedReportDTO> reportAccessRemoved() {
        return shareBookingRepository
            .findAllByDeletedAtIsNotNull()
            .stream()
            .sorted(Comparator.comparing(ShareBooking::getDeletedAt))
            .map(AccessRemovedReportDTO::new)
            .collect(Collectors.toList());
    }

    private PlaybackReportDTO toPlaybackReport(Audit audit) {
        // S28-3604 discovered audit details records Recording Id as recordingId _and_ recordinguid
        var auditDetails = audit.getAuditDetails() != null && !audit.getAuditDetails().isNull();
        UUID recordingId = null;
        if (auditDetails) {
            if (audit.getAuditDetails().hasNonNull("recordingId")) {
                recordingId = UUID.fromString(audit.getAuditDetails().get("recordingId").asText());
            } else if (audit.getAuditDetails().hasNonNull("recordinguid")) {
                recordingId = UUID.fromString(audit.getAuditDetails().get("recordinguid").asText());
            }
        }

        return new PlaybackReportDTO(
            audit,
            audit.getCreatedBy() != null
                ? userRepository
                .findById(audit.getCreatedBy())
                .orElse(appAccessRepository.findById(audit.getCreatedBy())
                            .map(AppAccess::getUser)
                            .orElse(portalAccessRepository.findById(audit.getCreatedBy())
                                        .map(PortalAccess::getUser)
                                        .orElse(null)))
                : null,
            recordingId != null
                ? recordingRepository.findById(recordingId).orElse(null)
                : null
        );
    }
}
