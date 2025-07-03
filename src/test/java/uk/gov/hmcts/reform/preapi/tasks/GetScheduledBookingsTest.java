package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.*;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class GetScheduledBookingsTest {
    private GetScheduledBookings getScheduledBookingsTask;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private BookingService bookingService;
    private SlackClient slackClient;
    private static final String ROBOT_USER_EMAIL = "example@example.com";

    @BeforeEach
    public void setUp() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        setUpAuthentication();
        bookingService = mock(BookingService.class);
        slackClient = mock(SlackClient.class);
        String platformEnv = "Local-Testing";

        getScheduledBookingsTask = new GetScheduledBookings(
            userService,
            userAuthenticationService,
            bookingService,
            slackClient,
            ROBOT_USER_EMAIL,
            platformEnv);
    }

    @Test
    public void testScheduledBookingsAreFound() {
        var today = LocalDate.now();
        var court = createCourt();
        var user = HelperFactory.createDefaultTestUser();

        var caseEntity1 = createCase(court, "case-1");
        var booking1 = createBooking(caseEntity1, Timestamp.valueOf(today.atStartOfDay().plusHours(10)));
        var captureSession1 = createCaptureSession(booking1, user);
        booking1.setCaptureSessions(Set.of(captureSession1));
        var booking1DTO = new BookingDTO(booking1);

        var caseEntity2 = createCase(court, "case-2");
        var booking2 = createBooking(caseEntity2, Timestamp.valueOf(today.atStartOfDay().plusHours(15)));
        var captureSession2 = createCaptureSession(booking2, user);
        booking2.setCaptureSessions(Set.of(captureSession2));
        var booking2DTO = new BookingDTO(booking2);

        var bookingDTOs = List.of(booking1DTO, booking2DTO);

        when(userAuthenticationService.validateUser(anyString()))
            .thenReturn(Optional.of(mock(UserAuthentication.class)));

        when(bookingService.findAllByScheduledFor(any(), any()))
            .thenReturn(bookingDTOs);

        getScheduledBookingsTask.run();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());
        String slackMessage = slackCaptor.getValue();

        assertThat(slackMessage)
            .contains("\\n\\n:warning: *Bookings scheduled for today:*\\n");

        bookingDTOs.forEach(bookingDTO -> {
            assertThat(slackMessage)
                .contains(String.format("\\n*Case Reference:* %s\\n", booking1DTO.getCaseDTO().getReference()));

            assertThat(slackMessage)
                .contains(String.format("\\n*Court Name:* %s\\n", booking1DTO.getCaseDTO().getCourt().getName()));

            assertThat(slackMessage)
                .contains(String.format("\\n*Booking ID:* %s\\n", booking1DTO.getId()));

            assertThat(slackMessage)
                .contains(String.format("\\n*Capture Session ID:* %s\\n", booking1DTO.getCaptureSessions().get(0).getId()));
        });
    }

    @Test
    public void testScheduledBookingsAreNotFound() {
        when(bookingService.findAllByScheduledFor(any(), any()))
            .thenReturn(List.of());

        getScheduledBookingsTask.run();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());

        assertThat(slackCaptor.getValue())
            .contains("\\n\\n:warning: *Bookings scheduled for today:*\\n");

        assertThat(slackCaptor.getValue())
            .contains("\\n\\t:white_check_mark: There are no scheduled bookings for today\\n");
    }

    private void setUpAuthentication() {
        var accessDTO = new AccessDTO();
        var baseAppAccessDTO = new BaseAppAccessDTO();
        baseAppAccessDTO.setId(UUID.randomUUID());
        accessDTO.setAppAccess(Set.of(baseAppAccessDTO));

        var userAuthentication = mock(UserAuthentication.class);
        when(userAuthentication.isAdmin()).thenReturn(true);
        when(userService.findByEmail(ROBOT_USER_EMAIL)).thenReturn(accessDTO);
        when(userAuthenticationService.validateUser(baseAppAccessDTO.getId().toString()))
            .thenReturn(Optional.of(userAuthentication));
    }

    private Court createCourt() {
        return HelperFactory.createCourt(CourtType.CROWN, "Test Court", null);
    }

    private Case createCase(Court court, String reference) {
        return HelperFactory.createCase(court, reference, true, null);
    }

    private Booking createBooking(Case caseEntity, Timestamp scheduledFor) {
        var booking = HelperFactory.createBooking(caseEntity, scheduledFor, null);
        booking.setId(UUID.randomUUID());
        return booking;
    }

    private CaptureSession createCaptureSession(Booking booking, User user) {
        return HelperFactory.createCaptureSession(
            booking, RecordingOrigin.PRE, "TestIngestAddress", "TestLiveOutputAddress",
            new Timestamp(System.currentTimeMillis()), user,
            new Timestamp(System.currentTimeMillis()), user,
            RecordingStatus.STANDBY,
            null);
    }
}
