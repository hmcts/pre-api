package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ShareBookingService.class)
public class ShareBookingServiceTest {

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ShareBookingRepository shareBookingRepository;

    @Autowired
    private ShareBookingService shareBookingService;

    @DisplayName("Share a booking by its id")
    @Test
    void shareBookingSuccess() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBookingDTO.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser().getId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser().getId())
        ).thenReturn(Optional.of(sharedWithUser));

        assertThat(shareBookingService.shareBookingById(shareBookingDTO)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Share a booking by its id when booking doesn't exist")
    @Test
    void shareBookingFailureBookingDoesntExist() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBookingDTO.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Booking: " + shareBookingDTO.getBookingId());
    }

    @DisplayName("Share a booking by its id when shared by user doesn't exist")
    @Test
    void shareBookingFailureSharedByUserDoesntExist() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBookingDTO.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());

        var bookingEntity = new Booking();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser().getId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Shared by User: " + shareBookingDTO.getSharedByUser());
    }

    @DisplayName("Share a booking by its id when shared with user doesn't exist")
    @Test
    void shareBookingFailureSharedWithUserDoesntExist() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBookingDTO.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());

        var bookingEntity = new Booking();
        var sharedByUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser().getId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser().getId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Shared with User: " + shareBookingDTO.getSharedWithUser());
    }

    @DisplayName("Share a booking by its id when share booking already exists")
    @Test
    void shareBookingFailureShareBookingAlreadyExists() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBookingDTO.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser().getId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser().getId())
        ).thenReturn(Optional.of(sharedWithUser));
        when(
            shareBookingRepository.existsById(shareBookingDTO.getId())
        ).thenReturn(true);

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.ConflictException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Conflict: Share booking already exists");
    }

    @DisplayName("Delete a share")
    @Test
    void deleteShareBookingSuccess() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setBooking(booking);

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.of(share));

        shareBookingService.deleteShareBookingById(booking.getId(), share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(booking.getId());
        verify(shareBookingRepository, times(1)).findById(share.getId());
        verify(shareBookingRepository, times(1)).deleteById(share.getId());
    }

    @DisplayName("Delete a share when booking not found")
    @Test
    void deleteShareBookingBookingNotFound() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setBooking(booking);

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(booking.getId()))
            .thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Booking: " + booking.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(booking.getId());
        verify(shareBookingRepository, never()).deleteById(share.getId());
    }

    @DisplayName("Delete a share when share does not exist")
    @Test
    void deleteShareBookingShareNotFound() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setBooking(booking);

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: ShareBooking: " + share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(booking.getId());
        verify(shareBookingRepository, times(1)).findById(share.getId());
        verify(shareBookingRepository, never()).deleteById(share.getId());
    }

    @DisplayName("Delete a share when share has already been deleted")
    @Test
    void deleteShareBookingAlreadyDeleted() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setBooking(booking);
        share.setDeletedAt(Timestamp.from(Instant.now()));

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.of(share));

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: ShareBooking: " + share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(booking.getId());
        verify(shareBookingRepository, times(1)).findById(share.getId());
        verify(shareBookingRepository, never()).deleteById(share.getId());
    }

    @DisplayName("Delete a share when booking id of the share found does not match request booking id")
    @Test
    void deleteShareBookingBookingFoundMismatch() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setBooking(booking);
        var searchBookingId = UUID.randomUUID();

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(searchBookingId))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.of(share));

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(searchBookingId, share.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: Found ShareBooking: "
                           + share.getId()
                           + ". Booking does not match: "
                           + searchBookingId
            );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(searchBookingId);
        verify(shareBookingRepository, times(1)).findById(share.getId());
        verify(shareBookingRepository, never()).deleteById(share.getId());
    }
}
