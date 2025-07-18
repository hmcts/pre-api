package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.alerts.SlackClient;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessage;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageJsonOptions;
import uk.gov.hmcts.reform.preapi.alerts.SlackMessageSection;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;

/**
 * Retrieves scheduled bookings for the current day.
 * Posts Slack message with the list of bookings.
 */
@Component
@Slf4j
public class GetScheduledBookings extends RobotUserTask {
    private final BookingService bookingService;
    private final SlackClient slackClient;
    private final String platformEnv;

    public GetScheduledBookings(UserService userService,
                                UserAuthenticationService userAuthenticationService,
                                BookingService bookingService,
                                SlackClient slackClient,
                                @Value("${cron-user-email}") String cronUserEmail,
                                @Value("${platform-env}") String platformEnv) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.bookingService = bookingService;
        this.slackClient = slackClient;
        this.platformEnv = platformEnv;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Signing in robot user with email {} on env {}", cronUserEmail, platformEnv);
        signInRobotUser();

        log.info("Running GetScheduledBookings task: looking for bookings for today");
        var bookings = getBookings();

        var slackMessage = createSlackMessage(bookings);
        log.info("About to send slack notification");
        slackClient.postSlackMessage(slackMessage);

        log.info("Completed CheckForMissingRecordings task");
    }

    private List<BookingDTO> getBookings() {
        return bookingService.findAllBookingsForToday()
                .stream()
                .toList();
    }

    private String createSlackMessage(List<BookingDTO> bookings) {
        List<SlackMessageSection> sections = List.of(createSlackMessageSection(bookings));

        var slackMessage = SlackMessage.builder()
            .sections(sections)
            .build();

        SlackMessageJsonOptions options = SlackMessageJsonOptions.builder()
                .showEnvironment(false)
                .showIcons(false)
                .build();

        return slackMessage.toJson(options);
    }

    private SlackMessageSection createSlackMessageSection(List<BookingDTO> bookings) {
        List<String> items = bookings.stream()
                .map(this::createSlackMessageItem)
                .toList();

        List<String> formattedItems = items.isEmpty()
                ? List.of()
                : List.of(String.join("\n\n", items));

        return new SlackMessageSection(
                "Bookings scheduled for today",
                formattedItems,
                "There are no scheduled bookings for today"
        );
    }

    private String createSlackMessageItem(BookingDTO booking) {
        return String.format(
                "*Case Reference:* %s\n*Court Name:* %s\n*Booking ID:* %s\n*Capture Session ID:* %s",
                booking.getCaseDTO().getReference(),
                booking.getCaseDTO().getCourt().getName(),
                booking.getId(),
                booking.getCaptureSessions().stream()
                        .map(session -> session.getId().toString())
                        .findFirst()
                        .orElse("No capture session found")
        );
    }
}
