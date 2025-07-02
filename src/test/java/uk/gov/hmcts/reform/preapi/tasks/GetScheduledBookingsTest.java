package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

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
        var booking1 = createBookingDTO(Timestamp.valueOf(today.atStartOfDay().plusHours(10)));
        var booking2 = createBookingDTO(Timestamp.valueOf(today.atStartOfDay().plusHours(15)));

        when(bookingService.findAllByScheduledFor(any(), any()))
            .thenReturn(List.of(booking1, booking2));

        getScheduledBookingsTask.run();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());

        // TODO assert on the content of the slack message
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

    private CaseDTO createCaseDTO() {
        var id = UUID.randomUUID();
        var caseDTO = new CaseDTO();
        caseDTO.setId(id);
        caseDTO.setReference(id.toString());
        return caseDTO;
    }

    private BookingDTO createBookingDTO(Timestamp scheduledFor) {
        var caseDTO = createCaseDTO();
        var booking = new BookingDTO();
        booking.setId(UUID.randomUUID());
        booking.setCaseDTO(caseDTO);
        booking.setScheduledFor(scheduledFor);
        return booking;
    }
}
