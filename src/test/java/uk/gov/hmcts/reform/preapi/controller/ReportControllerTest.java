package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.ReportController;
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingParticipantsReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ReportService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ReportControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private ScheduledTaskRunner taskRunner;

    @DisplayName("Should get a report containing a list of concurrent capture sessions")
    @Test
    void reportConcurrentCaptureSessionsSuccess() throws Exception {
        var reportItem = new ConcurrentCaptureSessionReportDTO();
        var timestamp = Timestamp.from(Instant.now());
        var timestampPlus1 = Timestamp.from(Instant.now().plusSeconds(3600));
        reportItem.setDate(DateTimeUtils.formatDate(timestamp));
        reportItem.setStartTime(DateTimeUtils.formatTime(timestamp));
        reportItem.setEndTime(DateTimeUtils.formatTime(timestampPlus1));
        reportItem.setDuration(Duration.ofMinutes(3));
        reportItem.setCourt("Example Court");
        reportItem.setCaseReference("ABC123");

        when(reportService.reportCaptureSessions()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/capture-sessions-concurrent"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].date").value(reportItem.getDate()))
            .andExpect(jsonPath("$[0].start_time").value(reportItem.getStartTime()))
            .andExpect(jsonPath("$[0].end_time").value(reportItem.getEndTime()))
            .andExpect(jsonPath("$[0].duration").value("00:03:00"));
    }

    @DisplayName("Should get a report containing a list of cases with the count of completed capture sessions")
    @Test
    void reportRecordingsPerCaseSuccess() throws Exception {
        var reportItem = new RecordingsPerCaseReportDTO();
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setCount(2);

        when(reportService.reportRecordingsPerCase()).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/recordings-per-case"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].count").value(reportItem.getCount()));
    }

    @DisplayName("Should get a report containing a list of edited recordings")
    @Test
    void reportEditsSuccess() throws Exception  {
        var reportItem = new EditReportDTO();
        reportItem.setCreatedAt(Timestamp.from(Instant.now()));
        reportItem.setVersion(2);
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setRecordingId(UUID.randomUUID());

        when(reportService.reportEdits()).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/edits"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].version").value(reportItem.getVersion()))
            .andExpect(jsonPath("$[0].recording_id").value(reportItem.getRecordingId().toString()));
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings")
    @Test
    void reportBookingsSharedSuccess() throws Exception {
        var reportItem = new SharedReportDTO();
        reportItem.setSharedAt(Timestamp.from(Instant.now()));
        reportItem.setAllocatedTo("example1@example.com");
        reportItem.setAllocatedToFullName("Example One");
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setAllocatedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        when(reportService.reportShared(null, null, null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_to_full_name").value(reportItem.getAllocatedToFullName()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].allocated_by_full_name").value(reportItem.getAllocatedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));

        verify(reportService, times(1)).reportShared(null, null, null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by court")
    @Test
    void reportBookingsSharedFilterCourtSuccess() throws Exception {
        var reportItem = new SharedReportDTO();
        reportItem.setSharedAt(Timestamp.from(Instant.now()));
        reportItem.setAllocatedTo("example1@example.com");
        reportItem.setAllocatedToFullName("Example One");
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setAllocatedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        var courtId = UUID.randomUUID();

        when(reportService.reportShared(courtId, null, null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("courtId", courtId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_to_full_name").value(reportItem.getAllocatedToFullName()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].allocated_by_full_name").value(reportItem.getAllocatedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));

        verify(reportService, times(1)).reportShared(courtId, null, null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by booking")
    @Test
    void reportBookingsSharedFilterBookingSuccess() throws Exception {
        var reportItem = new SharedReportDTO();
        reportItem.setSharedAt(Timestamp.from(Instant.now()));
        reportItem.setAllocatedTo("example1@example.com");
        reportItem.setAllocatedToFullName("Example One");
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setAllocatedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        when(reportService.reportShared(null, reportItem.getBookingId(), null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("bookingId", reportItem.getBookingId().toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_to_full_name").value(reportItem.getAllocatedToFullName()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].allocated_by_full_name").value(reportItem.getAllocatedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));

        verify(reportService, times(1)).reportShared(null, reportItem.getBookingId(), null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by user id")
    @Test
    void reportBookingsSharedFilterUserIdSuccess() throws Exception {
        var reportItem = new SharedReportDTO();
        reportItem.setSharedAt(Timestamp.from(Instant.now()));
        reportItem.setAllocatedTo("example1@example.com");
        reportItem.setAllocatedToFullName("Example One");
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setAllocatedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        var userId = UUID.randomUUID();

        when(reportService.reportShared(null, null, userId, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("sharedWithId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_to_full_name").value(reportItem.getAllocatedToFullName()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].allocated_by_full_name").value(reportItem.getAllocatedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));

        verify(reportService, times(1)).reportShared(null, null, userId, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by user email")
    @Test
    void reportBookingsSharedFilterUserEmailSuccess() throws Exception {
        var reportItem = new SharedReportDTO();
        reportItem.setSharedAt(Timestamp.from(Instant.now()));
        reportItem.setAllocatedTo("example1@example.com");
        reportItem.setAllocatedToFullName("Example One");
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setAllocatedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        when(reportService.reportShared(null, null, null, reportItem.getAllocatedTo())).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("sharedWithEmail", reportItem.getAllocatedTo()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_to_full_name").value(reportItem.getAllocatedToFullName()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].allocated_by_full_name").value(reportItem.getAllocatedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));

        verify(reportService, times(1)).reportShared(null, null, null, reportItem.getAllocatedTo());
    }

    @DisplayName("Should get a report containing a list of bookings with an available recording")
    @Test
    void reportScheduledSuccess() throws Exception {
        var reportItem = new ScheduleReportDTO();
        var timestamp = Timestamp.from(Instant.now());
        reportItem.setScheduledDate(DateTimeUtils.formatDate(timestamp));
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setCounty("Example County");
        reportItem.setPostcode("AB1 2CD");
        reportItem.setRegion("Example Region");
        reportItem.setDateOfBooking(DateTimeUtils.formatDate(timestamp));
        reportItem.setUser("example@example.com");

        when(reportService.reportScheduled()).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/schedules"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].scheduled_date").value(reportItem.getScheduledDate()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
            .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
            .andExpect(jsonPath("$[0].region").value(reportItem.getRegion()))
            .andExpect(jsonPath("$[0].date_of_booking").value(reportItem.getDateOfBooking()))
            .andExpect(jsonPath("$[0].user").value(reportItem.getUser()));
    }

    @DisplayName("Should get a report containing a list of completed capture sessions (with available recordings)")
    @Test
    void reportCompletedCaptureSessions() throws Exception {
        var reportItem = new CompletedCaptureSessionReportDTO();
        reportItem.setStartedAt(Timestamp.from(Instant.now()));
        reportItem.setFinishedAt(Timestamp.from(Instant.now()));
        reportItem.setDuration(Duration.ofMinutes(3L));
        reportItem.setScheduledFor(Timestamp.from(Instant.now()));
        reportItem.setCaseReference("ABC123");
        reportItem.setCountDefendants(1);
        reportItem.setCountWitnesses(5);
        reportItem.setRecordingStatus(RecordingStatus.RECORDING_AVAILABLE);
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());

        when(reportService.reportCompletedCaptureSessions()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/completed-capture-sessions"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].duration").value(reportItem.getDuration().toString()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].count_defendants").value(reportItem.getCountDefendants()))
            .andExpect(jsonPath("$[0].count_witnesses").value(reportItem.getCountWitnesses()))
            .andExpect(jsonPath("$[0].recording_status").value(reportItem.getRecordingStatus().toString()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()));
    }

    @DisplayName("Should get a report containing a list of share booking removals with case and user details")
    @Test
    void reportAccessRemoved() throws Exception {
        var reportItem = new AccessRemovedReportDTO();
        reportItem.setRemovedAt(Timestamp.from(Instant.now()));
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example court");
        reportItem.setRegions(Set.of());
        reportItem.setUserFullName("Example Person");
        reportItem.setUserEmail("example@example.com");
        reportItem.setRemovalReason("Example reason");

        when(reportService.reportAccessRemoved()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/share-bookings-removed"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].user_full_name").value(reportItem.getUserFullName()))
            .andExpect(jsonPath("$[0].user_email").value(reportItem.getUserEmail()))
            .andExpect(jsonPath("$[0].removal_reason").value(reportItem.getRemovalReason()));
    }

    @DisplayName("Should get a report containing a list of playback data for source 'PORTAL'")
    @Test
    void reportPlaybackPortalSuccess() throws Exception {
        var reportItem = createPlaybackReport();

        when(reportService.reportPlayback(AuditLogSource.PORTAL)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback")
                            .param("source", "PORTAL"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].user_full_name").value(reportItem.getUserFullName()))
            .andExpect(jsonPath("$[0].user_email").value(reportItem.getUserEmail()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].recording_id").value(reportItem.getRecordingId().toString()));

        verify(reportService, times(1)).reportPlayback(AuditLogSource.PORTAL);
    }

    @DisplayName("Should get a report containing a list of playback data for source 'APPLICATION'")
    @Test
    void reportPlaybackApplicationSuccess() throws Exception {
        var reportItem = createPlaybackReport();

        when(reportService.reportPlayback(AuditLogSource.APPLICATION)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback")
                            .param("source", "APPLICATION"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].user_full_name").value(reportItem.getUserFullName()))
            .andExpect(jsonPath("$[0].user_email").value(reportItem.getUserEmail()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].recording_id").value(reportItem.getRecordingId().toString()));

        verify(reportService, times(1)).reportPlayback(AuditLogSource.APPLICATION);
    }

    @DisplayName("Should get a report containing a list of playback data for no source")
    @Test
    void reportPlaybackAllSuccess() throws Exception {
        var reportItem = createPlaybackReport();

        when(reportService.reportPlayback(null)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].user_full_name").value(reportItem.getUserFullName()))
            .andExpect(jsonPath("$[0].user_email").value(reportItem.getUserEmail()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].recording_id").value(reportItem.getRecordingId().toString()));

        verify(reportService, times(1)).reportPlayback(null);
    }

    @DisplayName("Should get a report containing a list of participants and their related recordings")
    @Test
    void reportRecordingParticipantsSuccess() throws Exception {
        var dto = new RecordingParticipantsReportDTO();
        dto.setParticipantName("Participant Name");
        dto.setParticipantType(ParticipantType.WITNESS);
        dto.setRecordedAt(Timestamp.from(Instant.now()));
        dto.setCourtName("Court Name");
        dto.setCaseReference("1234567890");
        dto.setRecordingId(UUID.randomUUID());

        when(reportService.reportRecordingParticipants()).thenReturn(List.of(dto));

        mockMvc.perform(get("/reports/recording-participants"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$[0].participant_name").value(dto.getParticipantName()))
            .andExpect(jsonPath("$[0].participant_type").value(dto.getParticipantType().toString()))
            .andExpect(jsonPath("$[0].court_name").value(dto.getCourtName()))
            .andExpect(jsonPath("$[0].case_reference").value(dto.getCaseReference()))
            .andExpect(jsonPath("$[0].recording_id").value(dto.getRecordingId().toString()));
    }

    private PlaybackReportDTO createPlaybackReport() {
        return new PlaybackReportDTO(
            Timestamp.from(Instant.now()),
            "Example Person",
            "example@example.com",
            "CASE123456",
            "Example Court",
            Set.of(),
            UUID.randomUUID()
        );
    }
}
