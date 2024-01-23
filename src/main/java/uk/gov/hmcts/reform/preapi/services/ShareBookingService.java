package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

@Service
public class ShareBookingService {

    private final ShareBookingRepository shareBookingRepository;

    private final BookingRepository bookingRepository;

    private final UserRepository userRepository;

    @Autowired
    public ShareBookingService(ShareBookingRepository shareBookingRepository, BookingRepository bookingRepository,
                               UserRepository userRepository) {
        this.shareBookingRepository = shareBookingRepository;
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public UpsertResult shareBookingById(ShareBookingDTO shareBookingDTO) {
        if (shareBookingRepository.existsById(shareBookingDTO.getId())) {
            throw new ConflictException("Share booking already exists");
        }

        final var booking = bookingRepository.findById(shareBookingDTO.getBookingId())
            .orElseThrow(() -> new NotFoundException("Booking: " + shareBookingDTO.getBookingId()));
        final var sharedByUser = userRepository.findById(shareBookingDTO.getSharedByUserId())
            .orElseThrow(() -> new NotFoundException("Shared by User: " + shareBookingDTO.getSharedByUserId()));
        final var sharedWithUser = userRepository.findById(shareBookingDTO.getSharedWithUserId())
            .orElseThrow(() -> new NotFoundException("Shared with User: " + shareBookingDTO.getSharedWithUserId()));

        var shareBookingEntity = new ShareBooking();
        shareBookingEntity.setId(shareBookingDTO.getId());
        shareBookingEntity.setBooking(booking);
        shareBookingEntity.setSharedBy(sharedByUser);
        shareBookingEntity.setSharedWith(sharedWithUser);
        shareBookingRepository.save(shareBookingEntity);

        return UpsertResult.CREATED;
    }
}
