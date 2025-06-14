package uk.gov.hmcts.reform.preapi.security;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
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

    public AuthorisationService(BookingRepository bookingRepository,
                                CaseRepository caseRepository,
                                ParticipantRepository participantRepository,
                                CaptureSessionRepository captureSessionRepository,
                                RecordingRepository recordingRepository,
                                EditRequestRepository editRequestRepository) {
        this.bookingRepository = bookingRepository;
        this.caseRepository = caseRepository;
        this.participantRepository = participantRepository;
        this.captureSessionRepository = captureSessionRepository;
        this.recordingRepository = recordingRepository;
        this.editRequestRepository = editRequestRepository;
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
        return bookingId == null
            || authentication.isAdmin()
            || !bookingRepository.existsById(bookingId)
            || isBookingSharedWithUser(authentication, bookingId);
    }

    public boolean hasCaptureSessionAccess(UserAuthentication authentication, UUID captureSessionId) {
        if (captureSessionId == null || authentication.isAdmin()) {
            return true;
        }
        var entity = captureSessionRepository.findById(captureSessionId).orElse(null);
        return entity == null || hasBookingAccess(authentication, entity.getBooking().getId());
    }

    public boolean hasRecordingAccess(UserAuthentication authentication, UUID recordingId) {
        if (recordingId == null || authentication.isAdmin()) {
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
        if (caseId == null || authentication.isAdmin() || authentication.isPortalUser()) {
            return true;
        }
        var caseEntity = caseRepository.findById(caseId).orElse(null);
        return caseEntity == null
            || authentication.getCourtId().equals(caseEntity.getCourt().getId());
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

    public boolean canUpdateCaseState(UserAuthentication authentication, CreateCaseDTO dto) {
        return caseRepository.findById(dto.getId())
            .map(c -> (dto.getState() == null && c.getState() == CaseState.OPEN) || c.getState() == dto.getState())
            .orElse(true)
            || authentication.isAdmin()
            || authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_LEVEL_2"));
    }
}
