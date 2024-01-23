package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
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
        shareBookingDTO.setSharedByUserId(UUID.randomUUID());
        shareBookingDTO.setSharedWithUserId(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUserId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUserId())
        ).thenReturn(Optional.of(sharedWithUser));

        assertThat(shareBookingService.shareBookingById(shareBookingDTO)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Share a booking by its id when booking doesn't exist")
    @Test
    void shareBookingFailureBookingDoesntExist() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUserId(UUID.randomUUID());
        shareBookingDTO.setSharedWithUserId(UUID.randomUUID());

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
        shareBookingDTO.setSharedByUserId(UUID.randomUUID());
        shareBookingDTO.setSharedWithUserId(UUID.randomUUID());

        var bookingEntity = new Booking();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUserId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Shared by User: " + shareBookingDTO.getSharedByUserId());
    }

    @DisplayName("Share a booking by its id when shared with user doesn't exist")
    @Test
    void shareBookingFailureSharedWithUserDoesntExist() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUserId(UUID.randomUUID());
        shareBookingDTO.setSharedWithUserId(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUserId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUserId())
        ).thenReturn(Optional.empty());

        assertThatExceptionOfType(uk.gov.hmcts.reform.preapi.exception.NotFoundException.class)
            .isThrownBy(() -> {
                shareBookingService.shareBookingById(shareBookingDTO);
            })
            .withMessage("Not found: Shared with User: " + shareBookingDTO.getSharedWithUserId());
    }

    @DisplayName("Share a booking by its id when share booking already exists")
    @Test
    void shareBookingFailureShareBookingAlreadyExists() {
        var shareBookingDTO = new ShareBookingDTO();
        shareBookingDTO.setId(UUID.randomUUID());
        shareBookingDTO.setBookingId(UUID.randomUUID());
        shareBookingDTO.setSharedByUserId(UUID.randomUUID());
        shareBookingDTO.setSharedWithUserId(UUID.randomUUID());

        var bookingEntity = new Booking();
        var sharedByUser = new User();
        var sharedWithUser = new User();

        when(
            bookingRepository.findById(shareBookingDTO.getBookingId())
        ).thenReturn(Optional.of(bookingEntity));
        when(
            userRepository.findById(shareBookingDTO.getSharedByUserId())
        ).thenReturn(Optional.of(sharedByUser));
        when(
            userRepository.findById(shareBookingDTO.getSharedWithUserId())
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
}
