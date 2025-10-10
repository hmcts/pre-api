package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.alerts.SlackInfoBot;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GetScheduledBookingsTest {
    private GetScheduledBookings getScheduledBookingsTask;
    private UserService userService;
    private UserAuthenticationService userAuthenticationService;
    private BookingService bookingService;
    private SlackInfoBot slackClient;
    private static final String ROBOT_USER_EMAIL = "example@example.com";
    private static final String SLACK_MESSAGE_HEADER = "*Bookings scheduled for today:*\\n";
    private Court court1;
    private Court court2;
    private Court court3;
    private User user;

    @BeforeEach
    public void setUp() {
        userService = mock(UserService.class);
        userAuthenticationService = mock(UserAuthenticationService.class);
        setUpAuthentication();
        bookingService = mock(BookingService.class);
        slackClient = mock(SlackInfoBot.class);
        String platformEnv = "Local-Testing";

        getScheduledBookingsTask = new GetScheduledBookings(
            userService,
            userAuthenticationService,
            bookingService,
            slackClient,
            ROBOT_USER_EMAIL,
            platformEnv);

        court1 = HelperFactory.createCourt(CourtType.CROWN, "Court 1", null);
        court2 = HelperFactory.createCourt(CourtType.CROWN, "Court 2", null);
        court3 = HelperFactory.createCourt(CourtType.CROWN, "Court 3", null);
        user = HelperFactory.createDefaultTestUser();
    }

    @Test
    public void testScheduledBookings() {
        var today = LocalDate.now();

        var caseEntity1 = HelperFactory.createCase(court1, "case-1", true, null);
        var booking1 = createBooking(caseEntity1, Timestamp.valueOf(today.atStartOfDay().plusHours(10)));
        var captureSession1 = createCaptureSession(booking1, user);
        booking1.setCaptureSessions(Set.of(captureSession1));
        var booking1DTO = new BookingDTO(booking1);

        var caseEntity2 = HelperFactory.createCase(court2, "case-2", true, null);
        var booking2 = createBooking(caseEntity2, Timestamp.valueOf(today.atStartOfDay().plusHours(15)));
        var captureSession2 = createCaptureSession(booking2, user);
        booking2.setCaptureSessions(Set.of(captureSession2));
        var booking2DTO = new BookingDTO(booking2);

        // Booking without capture session
        var caseEntity3 = HelperFactory.createCase(court3, "case-3", true, null);
        var bookingWithoutCaptureSession = createBooking(
                caseEntity3,
                Timestamp.valueOf(today.atStartOfDay().plusHours(16)));
        var bookingWithoutCaptureSessionDTO = new BookingDTO(bookingWithoutCaptureSession);

        var bookingDTOs = List.of(booking1DTO, booking2DTO, bookingWithoutCaptureSessionDTO);

        when(bookingService.findAllBookingsForToday())
            .thenReturn(bookingDTOs);

        getScheduledBookingsTask.run();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());
        String slackMessage = slackCaptor.getValue();

        assertThat(slackMessage)
            .contains(SLACK_MESSAGE_HEADER);

        bookingDTOs.forEach(bookingDTO -> {
            String caseReference = bookingDTO.getCaseDTO().getReference();
            assertThat(slackMessage)
                .contains(String.format("\\n*Case Reference:* %s\\n", caseReference));

            String courtName = bookingDTO.getCaseDTO().getCourt().getName();
            assertThat(slackMessage)
                .contains(String.format("\\n*Court Name:* %s\\n", courtName));

            UUID bookingID = booking1DTO.getId();
            assertThat(slackMessage)
                .contains(String.format("\\n*Booking ID:* %s\\n", bookingID));

            if (bookingDTO.getId() == bookingWithoutCaptureSessionDTO.getId()) {
                assertThat(slackMessage)
                    .contains("\\n*Capture Session ID:* No capture session found\\n");
            } else {
                UUID captureSessionID = booking1DTO.getCaptureSessions().getFirst().getId();
                assertThat(slackMessage)
                    .contains(String.format("\\n*Capture Session ID:* %s\\n", captureSessionID));
            }
        });
    }

    @Test
    public void testScheduledBookingsAreNotFound() {
        when(bookingService.searchBy(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()))
                .thenReturn(new PageImpl<>(List.of()));

        getScheduledBookingsTask.run();

        ArgumentCaptor<String> slackCaptor = ArgumentCaptor.forClass(String.class);
        verify(slackClient).postSlackMessage(slackCaptor.capture());
        String slackMessage = slackCaptor.getValue();

        assertThat(slackMessage)
            .contains(SLACK_MESSAGE_HEADER);

        assertThat(slackMessage)
            .contains("\\nThere are no scheduled bookings for today\\n");
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
