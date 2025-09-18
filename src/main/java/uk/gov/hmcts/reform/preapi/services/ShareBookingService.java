package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class ShareBookingService {

    private final ShareBookingRepository shareBookingRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final EmailServiceFactory emailServiceFactory;

    @Autowired
    public ShareBookingService(ShareBookingRepository shareBookingRepository, BookingRepository bookingRepository,
                               UserRepository userRepository, EmailServiceFactory emailServiceFactory) {
        this.shareBookingRepository = shareBookingRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.emailServiceFactory = emailServiceFactory;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createShareBookingDTO)")
    public UpsertResult shareBookingById(CreateShareBookingDTO createShareBookingDTO) {
        if (shareBookingRepository.existsById(createShareBookingDTO.getId())
            || shareBookingRepository.existsBySharedWith_IdAndBooking_IdAndDeletedAtIsNull(
                createShareBookingDTO.getSharedWithUser(),
                createShareBookingDTO.getBookingId()
        )) {
            throw new ConflictException("Share booking already exists");
        }

        final var booking = bookingRepository.findById(createShareBookingDTO.getBookingId())
            .orElseThrow(() -> new NotFoundException("Booking: " + createShareBookingDTO.getBookingId()));

        if (booking.getCaseId().getState() == CaseState.CLOSED) {
            throw new ResourceInWrongStateException(
                "Booking",
                booking.getId(),
                booking.getCaseId().getState(),
                "OPEN or PENDING_CLOSURE"
            );
        }

        final var sharedByUser = userRepository.findById(createShareBookingDTO.getSharedByUser())
            .orElseThrow(() -> new NotFoundException("Shared by User: " + createShareBookingDTO.getSharedByUser()));
        final var sharedWithUser = userRepository.findById(createShareBookingDTO.getSharedWithUser())
            .orElseThrow(() -> new NotFoundException("Shared with User: " + createShareBookingDTO.getSharedWithUser()));

        var shareBookingEntity = new ShareBooking();
        shareBookingEntity.setId(createShareBookingDTO.getId());
        shareBookingEntity.setBooking(booking);
        shareBookingEntity.setSharedBy(sharedByUser);
        shareBookingEntity.setSharedWith(sharedWithUser);
        shareBookingRepository.save(shareBookingEntity);

        onBookingShared(shareBookingEntity);

        return UpsertResult.CREATED;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #bookingId)")
    public void deleteShareBookingById(UUID bookingId, UUID shareId) {
        if (!bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }

        var share = shareBookingRepository
            .findById(shareId)
            .orElseThrow(() -> new NotFoundException("ShareBooking: " + shareId));

        if (share.isDeleted()) {
            throw new NotFoundException("ShareBooking: " + shareId);
        }

        if (!share.getBooking().getId().equals(bookingId)) {
            throw new NotFoundException("Found ShareBooking: " + shareId + ". Booking does not match: " + bookingId);
        }

        share.setDeleteOperation(true);
        share.setDeletedAt(Timestamp.from(Instant.now()));

        shareBookingRepository.saveAndFlush(share);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Set<ShareBooking> deleteCascade(Booking booking) {
        return shareBookingRepository
            .findAllByBookingAndDeletedAtIsNull(booking)
            .stream()
            .peek(share -> {
                share.setSoftDeleteOperation(true);
                share.setDeletedAt(Timestamp.from(Instant.now()));
                shareBookingRepository.save(share);
            })
            .collect(Collectors.toSet());
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Set<ShareBooking> deleteCascade(Case aCase) {
        return bookingRepository.findAllByCaseIdAndDeletedAtIsNull(aCase)
            .stream()
            .map(this::deleteCascade)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public Set<ShareBooking> getSharesForCase(Case c) {
        return bookingRepository.findAllByCaseIdAndDeletedAtIsNull(c)
            .stream()
            .map(shareBookingRepository::findAllByBookingAndDeletedAtIsNull)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #bookingId)")
    public Page<ShareBookingDTO> getShareLogsForBooking(UUID bookingId, Pageable pageable) {
        if (!bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)) {
            throw new NotFoundException("Booking: " + bookingId);
        }

        return shareBookingRepository
            .findByBooking_IdOrderBySharedWith_FirstNameAsc(bookingId, pageable)
            .map(ShareBookingDTO::new);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onBookingShared(ShareBooking s) {
        // only send an email if there is a recording for this booking
        if (emailServiceFactory.isEnabled()
            && Stream.ofNullable(s.getBooking().getCaptureSessions())
                     .flatMap(Set::stream)
                     .anyMatch(c -> c.getStatus().equals(RecordingStatus.RECORDING_AVAILABLE))) {

            try {
                log.info("onBookingShared: Booking({})", s.getBooking().getId());
                emailServiceFactory.getEnabledEmailService()
                                   .recordingReady(s.getSharedWith(), s.getBooking().getCaseId());
            } catch (Exception e) {
                log.error("Failed to notify user " + s.getSharedWith().getId()
                              + " of shared booking: " + s.getBooking().getId());
            }
        }
    }
}
