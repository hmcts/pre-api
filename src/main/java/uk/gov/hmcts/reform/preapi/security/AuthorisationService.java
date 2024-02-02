package uk.gov.hmcts.reform.preapi.security;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.UUID;

@Service
public class AuthorisationService {
    private final BookingRepository bookingRepository;
    private final CaseRepository caseRepository;
    private final ParticipantRepository participantRepository;
    private final CaptureSessionRepository captureSessionRepository;

    public AuthorisationService(BookingRepository bookingRepository,
                                CaseRepository caseRepository,
                                ParticipantRepository participantRepository,
                                CaptureSessionRepository captureSessionRepository) {
        this.bookingRepository = bookingRepository;
        this.caseRepository = caseRepository;
        this.participantRepository = participantRepository;
        this.captureSessionRepository = captureSessionRepository;
    }

    private boolean isBookingSharedWithUser(UserAuthentication authentication, UUID bookingId) {
        return authentication.getSharedBookings().stream().anyMatch(b -> b.equals(bookingId));
    }

    public boolean hasCourtAccess(UserAuthentication authentication, UUID courtId) {
        return courtId == null
            || authentication.isSuperUser()
            || authentication.getCourtId().equals(courtId);
    }

    public boolean hasBookingAccess(UserAuthentication authentication, UUID bookingId) {
        return bookingId == null
            || authentication.isSuperUser()
            || !bookingRepository.existsById(bookingId)
            || isBookingSharedWithUser(authentication, bookingId);
    }

    public boolean hasCaptureSessionAccess(UserAuthentication authentication, UUID captureSessionId) {
        if (captureSessionId == null || authentication.isSuperUser()) {
            return true;
        }
        var entity = captureSessionRepository.findById(captureSessionId).orElse(null);
        return entity == null || hasBookingAccess(authentication, entity.getBooking().getId());
    }

    public boolean hasParticipantAccess(UserAuthentication authentication, UUID participantId) {
        if (participantId == null || authentication.isSuperUser()) {
            return true;
        }

        var participant = participantRepository.findById(participantId).orElse(null);
        return participant == null || hasCaseAccess(authentication, participant.getCaseId().getId());
    }

    public boolean hasCaseAccess(UserAuthentication authentication, UUID caseId) {
        if (caseId == null || authentication.isSuperUser()) {
            return true;
        }
        var caseEntity = caseRepository.findById(caseId).orElse(null);
        return caseEntity == null
            || authentication.getCourtId().equals(caseEntity.getCourt().getId());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateBookingDTO dto) {
        return hasBookingAccess(authentication, dto.getId())
            && hasCaseAccess(authentication, dto.getCaseId())
            && dto.getParticipants().stream().allMatch(p -> hasUpsertAccess(authentication, p));
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateParticipantDTO dto) {
        return hasParticipantAccess(authentication, dto.getId());
    }

    public boolean hasUpsertAccess(UserAuthentication authentication, CreateCaptureSessionDTO dto) {
        return hasCaptureSessionAccess(authentication, dto.getId())
            && hasBookingAccess(authentication, dto.getBookingId());
    }
}
