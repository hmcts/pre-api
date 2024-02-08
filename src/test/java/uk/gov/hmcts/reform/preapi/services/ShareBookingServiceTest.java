package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
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
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser())
        ).thenReturn(Optional.of(sharedWithUser));

        assertThat(shareBookingService.shareBookingById(shareBookingDTO)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Share a booking by its id when booking doesn't exist")
    @Test
    void shareBookingFailureBookingDoesntExist() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Booking: " + shareBookingDTO.getBookingId());
    }

    @DisplayName("Share a booking by its id when shared by user doesn't exist")
    @Test
    void shareBookingFailureSharedByUserDoesntExist() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var bookingEntity = new Booking();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser())
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
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser())
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
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUser())
        ).thenReturn(Optional.of(sharedWithUser));
        when(
            shareBookingRepository.existsById(shareBookingDTO.getId())
        ).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
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

        when(bookingRepository.existsByIdAndDeletedAtIsNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.of(share));

        shareBookingService.deleteShareBookingById(booking.getId(), share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(booking.getId());
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

        when(bookingRepository.existsByIdAndDeletedAtIsNull(booking.getId()))
            .thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Booking: " + booking.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(booking.getId());
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

        when(bookingRepository.existsByIdAndDeletedAtIsNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: ShareBooking: " + share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(booking.getId());
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

        when(bookingRepository.existsByIdAndDeletedAtIsNull(booking.getId()))
            .thenReturn(true);
        when(shareBookingRepository.findById(share.getId()))
            .thenReturn(Optional.of(share));

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.deleteShareBookingById(booking.getId(), share.getId())
        ).getMessage();

        assertThat(message)
            .isEqualTo("Not found: ShareBooking: " + share.getId());

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(booking.getId());
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

        when(bookingRepository.existsByIdAndDeletedAtIsNull(searchBookingId))
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

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(searchBookingId);
        verify(shareBookingRepository, times(1)).findById(share.getId());
        verify(shareBookingRepository, never()).deleteById(share.getId());
    }

    @DisplayName("Should get all share logs for a booking")
    @Test
    void getShareLogsForBooking() {
        var booking = new Booking();
        booking.setId(UUID.randomUUID());

        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");

        var shareBooking = new ShareBooking();
        shareBooking.setId(UUID.randomUUID());
        shareBooking.setBooking(booking);
        shareBooking.setSharedWith(user);
        shareBooking.setSharedBy(user);

        when(bookingRepository.existsByIdAndDeletedAtIsNull(booking.getId())).thenReturn(true);
        when(shareBookingRepository.findAllByBooking_Id(booking.getId(), null))
            .thenReturn(new PageImpl<>(List.of(shareBooking)));

        var models = shareBookingService.getShareLogsForBooking(booking.getId(), null);

        assertThat(models.getContent().size()).isEqualTo(1);
        assertThat(models.getContent().getFirst().getBookingId()).isEqualTo(booking.getId());
        assertThat(models.getContent().getFirst().getBookingId()).isEqualTo(booking.getId());
        assertThat(models.getContent().getFirst().getBookingId()).isEqualTo(booking.getId());
        assertThat(models.getContent().getFirst().getSharedWithUser().getId()).isEqualTo(user.getId());
        assertThat(models.getContent().getFirst().getSharedByUser().getId()).isEqualTo(user.getId());
    }

    @DisplayName("Should throw not found error when booking is not found")
    @Test
    void getShareLogsForBookingNotFound() {
        var bookingId = UUID.randomUUID();

        when(bookingRepository.existsByIdAndDeletedAtIsNull(bookingId)).thenReturn(false);

        var message = assertThrows(
            NotFoundException.class,
            () -> shareBookingService.getShareLogsForBooking(bookingId, null)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Booking: " + bookingId);

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNull(bookingId);
    }
}
