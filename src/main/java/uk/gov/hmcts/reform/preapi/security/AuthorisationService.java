package uk.gov.hmcts.reform.preapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.EditRequestRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.Set;
import java.util.UUID;

@Service
public class AuthorisationService {
    private final BookingRepository bookingRepository;
    private final CaseRepository caseRepository;
    private final ParticipantRepository participantRepository;
    private final CaptureSessionRepository captureSessionRepository;
    private final RecordingRepository recordingRepository;
    private final EditRequestRepository editRequestRepository;

    private final boolean enableMigratedData;

    public AuthorisationService(BookingRepository bookingRepository,
                                CaseRepository caseRepository,
                                ParticipantRepository participantRepository,
                                CaptureSessionRepository captureSessionRepository,
                                RecordingRepository recordingRepository,
                                EditRequestRepository editRequestRepository,
                                @Value("${migration.enableMigratedData:false}") boolean enableMigratedData) {
        this.bookingRepository = bookingRepository;
        this.caseRepository = caseRepository;
        this.participantRepository = participantRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.editRequestRepository = editRequestRepository;
        this.enableMigratedData = enableMigratedData;
    }

    private boolean isBookingSharedWithUser(UserAuthentication authentication, UUID bookingId) {
        var booking = bookingRepository.findById(bookingId);

        return booking.isPresent() && (authentication.isAppUser()
            && booking.get().getCaseId().getCourt().getId().equals(authentication.getCourtId()
        ) || (authentication.isPortalUser()
            && authentication.getSharedBookings().stream().anyMatch(b -> b.equals(bookingId))));
    }

    public boolean hasCourtAccess(UserAuthentication authentication, UUID courtId) {
        return courtId == null
            || authentication.isAdmin()
            || authentication.getCourtId().equals(courtId);
    }

    public boolean hasBookingAccess(UserAuthentication authentication, UUID bookingId) {
        if (bookingId == null) {
            return true;
        }
        var booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null) {
            return true;
        }

        if (!enableMigratedData && isVodafoneData(booking.getCaseId())) {
            return canViewVodafoneData(authentication);
        }

        return authentication.isAdmin()
            || isBookingSharedWithUser(authentication, bookingId);
    }

    public boolean hasCaptureSessionAccess(UserAuthentication authentication, UUID captureSessionId) {
        if (captureSessionId == null || (enableMigratedData && authentication.isAdmin())) {
            return true;
        }
        CaptureSession entity = captureSessionRepository.findById(captureSessionId).orElse(null);
        if  (entity == null) {
            return true;
        }

        return (!enableMigratedData && entity.getOrigin().equals(RecordingOrigin.VODAFONE))
            ? canViewVodafoneData(authentication) && hasBookingAccess(authentication, captureSessionId)
            : hasBookingAccess(authentication, entity.getBooking().getId());
    }

    public boolean hasRecordingAccess(UserAuthentication authentication, UUID recordingId) {
        if (recordingId == null || (enableMigratedData && authentication.isAdmin())) {
            return true;
        }
        var entity = recordingRepository.findById(recordingId).orElse(null);
        return entity == null || hasCaptureSessionAccess(authentication, entity.getCaptureSession().getId());
    }

    public boolean hasParticipantAccess(UserAuthentication authentication, UUID participantId) {
        if (participantId == null || authentication.isAdmin()) {
            return true;
        }

        var participant = participantRepository.findById(participantId).orElse(null);
        return participant == null || hasCaseAccess(authentication, participant.getCaseId().getId());
    }

    public boolean hasCaseAccess(UserAuthentication authentication, UUID caseId) {
        if (caseId == null) {
            return true;
        }

        Case caseEntity = caseRepository.findById(caseId).orElse(null);
        if (caseEntity == null) {
            return true;
        }

        boolean isSameCourt = authentication.getCourtId().equals(caseEntity.getCourt().getId());
        return !enableMigratedData && caseEntity.getOrigin() == RecordingOrigin.VODAFONE
            ? canViewVodafoneData(authentication)
            : authentication.isAdmin()
                || authentication.isPortalUser()
                || isSameCourt;
    }

    public boolean hasEditRequestAccess(UserAuthentication authentication, UUID id) {
        if (id == null || authentication.isAdmin()) {
            return true;
        }
        try {
            var request = editRequestRepository.findByIdNotLocked(id).orElse(null);
            return request == null || hasRecordingAccess(authentication, request.getSourceRecording().getId());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateBookingDTO dto) {
        return hasBookingAccess(authentication, dto.getId())
            && hasCaseAccess(authentication, dto.getCaseId())
            && hasUpsertAccess(authentication, dto.getParticipants());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateParticipantDTO dto) {
        return hasParticipantAccess(authentication, dto.getId());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, Set<CreateParticipantDTO> participants) {
        return participants.stream().allMatch(p -> hasUpsertAccess(authentication, p));
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateCaptureSessionDTO dto) {
        return hasCaptureSessionAccess(authentication, dto.getId())
            && hasBookingAccess(authentication, dto.getBookingId());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateRecordingDTO dto) {
        return hasCaptureSessionAccess(authentication, dto.getCaptureSessionId())
            && hasRecordingAccess(authentication, dto.getParentRecordingId());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateShareBookingDTO dto) {
        return authentication.getUserId().equals(dto.getSharedByUser())
            && (authentication.isAdmin() || hasBookingAccess(authentication, dto.getBookingId()));
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateCaseDTO dto) {
        return hasCaseAccess(authentication, dto.getId())
            && hasCourtAccess(authentication, dto.getCourtId())
            && hasUpsertAccess(authentication, dto.getParticipants())
            && canUpdateCaseState(authentication, dto);
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateEditRequestDTO dto) {
        return hasRecordingAccess(authentication, dto.getSourceRecordingId());
    }

    public boolean canViewDeleted(UserAuthentication authentication) {
        return authentication.isAdmin();
    }

    public boolean canViewVodafoneData(UserAuthentication authentication) {
        return enableMigratedData || authentication.hasRole("ROLE_SUPER_USER");
    }

    public boolean isVodafoneData(Case caseEntity) {
        return caseEntity.getOrigin() == RecordingOrigin.VODAFONE;
    }

    public boolean canUpdateCaseState(UserAuthentication authentication, CreateCaseDTO dto) {
        return caseRepository.findById(dto.getId())
            .map(c -> (dto.getState() == null && c.getState() == CaseState.OPEN) || c.getState() == dto.getState())
            .orElse(true)
            || authentication.isAdmin()
            || authentication.hasRole("ROLE_LEVEL_2");
    }

    public boolean canSearchByCaseClosed(UserAuthentication authentication, Boolean caseOpen) {
        return caseOpen == null
            || caseOpen
            || authentication.isAdmin()
            || authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LEVEL_2"));
    }
}
