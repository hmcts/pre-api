package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ShareBookingService.class)
public class ShareBookingServiceTest {

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private ShareBookingRepository shareBookingRepository;

    @MockitoBean
    private EmailServiceFactory emailServiceFactory;

    @MockitoBean
    private GovNotify govNotify;

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

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.OPEN);
        var bookingEntity = new Booking();
        bookingEntity.setId(shareBookingDTO.getBookingId());
        bookingEntity.setCaseId(aCase);

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

    @DisplayName("Share a booking by its id when booking's associated case has been closed")
    @Test
    void shareBookingFailureCaseClosed() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.CLOSED);
        var booking = new Booking();
        booking.setId(shareBookingDTO.getBookingId());
        booking.setCaseId(aCase);

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(booking));

        assertThatExceptionOfType(ResourceInWrongStateException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage(
                "Resource Booking("
                    + shareBookingDTO.getBookingId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN or PENDING_CLOSURE.");
    }

    @DisplayName("Share a booking by its id when shared by user doesn't exist")
    @Test
    void shareBookingFailureSharedByUserDoesntExist() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.OPEN);
        var bookingEntity = new Booking();
        bookingEntity.setId(shareBookingDTO.getBookingId());
        bookingEntity.setCaseId(aCase);

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUser())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
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

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.OPEN);
        var bookingEntity = new Booking();
        bookingEntity.setId(shareBookingDTO.getBookingId());
        bookingEntity.setCaseId(aCase);
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
    void shareBookingFailureShareBookingIdAlreadyExists() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        when(
            shareBookingRepository.existsById(shareBookingDTO.getId())
        ).thenReturn(true);

        assertThatExceptionOfType(ConflictException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Conflict: Share booking already exists");
    }

    @DisplayName("Share a booking by its id when share booking already exists")
    @Test
    void shareBookingFailureShareBookingAlreadyExists() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        when(
            shareBookingRepository.existsById(shareBookingDTO.getId())
        ).thenReturn(false);
        when(
            shareBookingRepository.existsBySharedWith_IdAndBooking_IdAndDeletedAtIsNull(
                shareBookingDTO.getSharedWithUser(),
                shareBookingDTO.getBookingId()
            )
        ).thenReturn(true);

        var message = assertThrows(
            ConflictException.class,
            () -> shareBookingService.shareBookingById(shareBookingDTO)
        ).getMessage();

        assertThat(message).isEqualTo("Conflict: Share booking already exists");
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
        verify(shareBookingRepository, times(1)).saveAndFlush(share);
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
        when(shareBookingRepository.findByBooking_IdOrderBySharedWith_FirstNameAsc(booking.getId(), null))
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

    @DisplayName("Should delete all shares for a case")
    @Test
    void deleteCascadeForCase() {
        var aCase = new Case();
        var booking1 = new Booking();
        booking1.setCaseId(aCase);
        var booking2 = new Booking();
        booking2.setCaseId(aCase);
        var share1 = new ShareBooking();
        share1.setBooking(booking1);
        var share2 = new ShareBooking();
        share2.setBooking(booking1);
        var share3 = new ShareBooking();
        share3.setBooking(booking2);

        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(aCase)).thenReturn(List.of(booking1, booking2));
        when(shareBookingRepository.findAllByBookingAndDeletedAtIsNull(booking1)).thenReturn(List.of(share1, share2));
        when(shareBookingRepository.findAllByBookingAndDeletedAtIsNull(booking2)).thenReturn(List.of(share3));

        shareBookingService.deleteCascade(aCase);

        assertThat(share1.isDeleted()).isTrue();
        assertThat(share2.isDeleted()).isTrue();
        assertThat(share3.isDeleted()).isTrue();

        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(aCase);
        verify(shareBookingRepository, times(2)).findAllByBookingAndDeletedAtIsNull(any(Booking.class));
        verify(shareBookingRepository, times(3)).save(any(ShareBooking.class));
    }

    @DisplayName(("Should notify user by email on booking shared when email service is enabled"))
    @Test
    void doNotNotifyUserByEmailOnBookingShared() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.OPEN);
        var bookingEntity = new Booking();
        bookingEntity.setId(shareBookingDTO.getBookingId());
        bookingEntity.setCaseId(aCase);

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

        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(govNotify);

        shareBookingService.shareBookingById(shareBookingDTO);

        verify(govNotify, times(0)).recordingReady(any(), any());
    }

    @DisplayName(("Should notify user by email on booking shared when email service is enabled"))
    @Test
    void notifyUserByEmailOnBookingShared() {
        var shareBookingDTO = new CreateShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUser(UUID.randomUUID());
        shareBookingDTO.setSharedWithUser(UUID.randomUUID());

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.OPEN);
        var bookingEntity = new Booking();
        bookingEntity.setId(shareBookingDTO.getBookingId());
        bookingEntity.setCaseId(aCase);

        // add a capture session with recording to the booking entity
        var captureSession = new CaptureSession();
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        bookingEntity.setCaptureSessions(Set.of(captureSession));

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

        when(emailServiceFactory.isEnabled()).thenReturn(true);
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(govNotify);

        shareBookingService.shareBookingById(shareBookingDTO);

        verify(govNotify, times(1)).recordingReady(any(), any());
    }
}
