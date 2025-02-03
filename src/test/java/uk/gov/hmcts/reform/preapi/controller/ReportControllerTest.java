package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.BeforeAll;
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
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
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
import java.util.TimeZone;
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

    @BeforeAll
    static void setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

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
        var timestamp = Timestamp.from(Instant.now());
        reportItem.setEditDate(DateTimeUtils.formatDate(timestamp));
        reportItem.setEditTime(DateTimeUtils.formatTime(timestamp));
        reportItem.setTimezone(DateTimeUtils.getTimezoneAbbreviation(timestamp));
        reportItem.setVersion(2);
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setCounty("Example County");
        reportItem.setPostcode("AB1 2CD");
        reportItem.setRegion("Somewhere");

        when(reportService.reportEdits()).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/edits"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].edit_date").value(reportItem.getEditDate()))
            .andExpect(jsonPath("$[0].edit_time").value(reportItem.getEditTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].version").value(reportItem.getVersion()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
            .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
            .andExpect(jsonPath("$[0].region").value(reportItem.getRegion()));
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings")
    @Test
    void reportBookingsSharedSuccess() throws Exception {
        var reportItem = createSharedReport();

        when(reportService.reportShared(null, null, null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].share_date").value(reportItem.getShareDate()))
            .andExpect(jsonPath("$[0].share_time").value(reportItem.getShareTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].shared_with").value(reportItem.getSharedWith()))
            .andExpect(jsonPath("$[0].shared_with_full_name").value(reportItem.getSharedWithFullName()))
            .andExpect(jsonPath("$[0].granted_by").value(reportItem.getGrantedBy()))
            .andExpect(jsonPath("$[0].granted_by_full_name").value(reportItem.getGrantedByFullName()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
            .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
            .andExpect(jsonPath("$[0].region").value(reportItem.getRegion()));

        verify(reportService, times(1)).reportShared(null, null, null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by court")
    @Test
    void reportBookingsSharedFilterCourtSuccess() throws Exception {
        var reportItem = createSharedReport();
        var courtId = UUID.randomUUID();

        when(reportService.reportShared(courtId, null, null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("courtId", courtId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].share_date").value(reportItem.getShareDate()))
            .andExpect(jsonPath("$[0].share_time").value(reportItem.getShareTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].shared_with").value(reportItem.getSharedWith()))
            .andExpect(jsonPath("$[0].shared_with_full_name").value(reportItem.getSharedWithFullName()))
            .andExpect(jsonPath("$[0].granted_by").value(reportItem.getGrantedBy()))
            .andExpect(jsonPath("$[0].granted_by_full_name").value(reportItem.getGrantedByFullName()));


        verify(reportService, times(1)).reportShared(courtId, null, null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by booking")
    @Test
    void reportBookingsSharedFilterBookingSuccess() throws Exception {
        var reportItem = createSharedReport();
        var searchId = UUID.randomUUID();

        when(reportService.reportShared(null, searchId, null, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("bookingId", searchId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].share_date").value(reportItem.getShareDate()))
            .andExpect(jsonPath("$[0].share_time").value(reportItem.getShareTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].shared_with").value(reportItem.getSharedWith()))
            .andExpect(jsonPath("$[0].shared_with_full_name").value(reportItem.getSharedWithFullName()))
            .andExpect(jsonPath("$[0].granted_by").value(reportItem.getGrantedBy()))
            .andExpect(jsonPath("$[0].granted_by_full_name").value(reportItem.getGrantedByFullName()));

        verify(reportService, times(1)).reportShared(null, searchId, null, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by user id")
    @Test
    void reportBookingsSharedFilterUserIdSuccess() throws Exception {
        var reportItem = createSharedReport();

        var userId = UUID.randomUUID();

        when(reportService.reportShared(null, null, userId, null)).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("sharedWithId", userId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].share_date").value(reportItem.getShareDate()))
            .andExpect(jsonPath("$[0].share_time").value(reportItem.getShareTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].shared_with").value(reportItem.getSharedWith()))
            .andExpect(jsonPath("$[0].shared_with_full_name").value(reportItem.getSharedWithFullName()))
            .andExpect(jsonPath("$[0].granted_by").value(reportItem.getGrantedBy()))
            .andExpect(jsonPath("$[0].granted_by_full_name").value(reportItem.getGrantedByFullName()));

        verify(reportService, times(1)).reportShared(null, null, userId, null);
    }

    @DisplayName("Should get a report containing a list of details relating to shared bookings filtered by user email")
    @Test
    void reportBookingsSharedFilterUserEmailSuccess() throws Exception {
        var reportItem = createSharedReport();

        when(reportService.reportShared(null, null, null, reportItem.getSharedWith())).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings")
                            .param("sharedWithEmail", reportItem.getSharedWith()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].share_date").value(reportItem.getShareDate()))
            .andExpect(jsonPath("$[0].share_time").value(reportItem.getShareTime()))
            .andExpect(jsonPath("$[0].timezone").value(reportItem.getTimezone()))
            .andExpect(jsonPath("$[0].shared_with").value(reportItem.getSharedWith()))
            .andExpect(jsonPath("$[0].shared_with_full_name").value(reportItem.getSharedWithFullName()))
            .andExpect(jsonPath("$[0].granted_by").value(reportItem.getGrantedBy()))
            .andExpect(jsonPath("$[0].granted_by_full_name").value(reportItem.getGrantedByFullName()));

        verify(reportService, times(1)).reportShared(null, null, null, reportItem.getSharedWith());
    }

    @DisplayName("Should get a report containing a list of bookings with an available recording")
    @Test
    void reportScheduledSuccess() throws Exception {
        var reportItem = new ScheduleReportDTO();
        reportItem.setScheduledFor(Timestamp.from(Instant.now()));
        reportItem.setBookingCreatedAt(Timestamp.from(Instant.now()));
        reportItem.setCaseReference("ABC123");
        reportItem.setCaptureSessionUser("example@example.com");
        reportItem.setCourt("Example court");
        reportItem.setRegions(Set.of());

        when(reportService.reportScheduled()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/schedules"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].capture_session_user").value(reportItem.getCaptureSessionUser()));
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
        var timestamp = Timestamp.from(Instant.now());
        reportItem.setRemovedDate(DateTimeUtils.formatDate(timestamp));
        reportItem.setRemovedTime(DateTimeUtils.formatTime(timestamp));
        reportItem.setRemovedTimezone(DateTimeUtils.getTimezoneAbbreviation(timestamp));
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example court");
        reportItem.setCounty("Kent");
        reportItem.setPostcode("AB1 2CD");
        reportItem.setRegion("Somewhere");
        reportItem.setFullName("Example Person");
        reportItem.setUserEmail("example@example.com");

        when(reportService.reportAccessRemoved()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/share-bookings-removed"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].removed_date").value(reportItem.getRemovedDate()))
            .andExpect(jsonPath("$[0].removed_time").value(reportItem.getRemovedTime()))
            .andExpect(jsonPath("$[0].removed_timezone").value(reportItem.getRemovedTimezone()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
            .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
            .andExpect(jsonPath("$[0].full_name").value(reportItem.getFullName()))
            .andExpect(jsonPath("$[0].user_email").value(reportItem.getUserEmail()));
    }

    @DisplayName("Should get a report containing a list of playback data for source 'PORTAL'")
    @Test
    void reportPlaybackPortalSuccess() throws Exception {
        var reportItem = createPlaybackReport(Timestamp.valueOf("2025-01-01 00:00:00"));

        when(reportService.reportPlayback(AuditLogSource.PORTAL)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback")
                            .param("source", "PORTAL"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].playback_date").value("01/01/2025"))
            .andExpect(jsonPath("$[0].playback_time").value("00:00:00"))
            .andExpect(jsonPath("$[0].playback_time_zone").value("GMT"))
            .andExpect(jsonPath("$[0].user").value(reportItem.getUser()))
            .andExpect(jsonPath("$[0].user_organisation").value(reportItem.getUserOrganisation()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court_name").value(reportItem.getCourtName()))
            .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
            .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
            .andExpect(jsonPath("$[0].regions").value(reportItem.getRegions()));

        verify(reportService, times(1)).reportPlayback(AuditLogSource.PORTAL);
    }

    @DisplayName("Should get a report containing a list of playback data for source 'APPLICATION'")
    @Test
    void reportPlaybackApplicationSuccess() throws Exception {
        var reportItem = createPlaybackReport(Timestamp.valueOf("2025-07-01 00:00:00"));

        when(reportService.reportPlayback(AuditLogSource.APPLICATION)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback")
                            .param("source", "APPLICATION"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$[0].playback_date").value("01/07/2025"))
               .andExpect(jsonPath("$[0].playback_time").value("01:00:00"))
               .andExpect(jsonPath("$[0].playback_time_zone").value("BST"))
               .andExpect(jsonPath("$[0].user").value(reportItem.getUser()))
               .andExpect(jsonPath("$[0].user_organisation").value(reportItem.getUserOrganisation()))
               .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
               .andExpect(jsonPath("$[0].court_name").value(reportItem.getCourtName()))
               .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
               .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
               .andExpect(jsonPath("$[0].regions").value(reportItem.getRegions()));

        verify(reportService, times(1)).reportPlayback(AuditLogSource.APPLICATION);
    }

    @DisplayName("Should get a report containing a list of playback data for no source")
    @Test
    void reportPlaybackAllSuccess() throws Exception {
        var reportItem = createPlaybackReport(Timestamp.valueOf("2025-01-01 00:00:00"));

        when(reportService.reportPlayback(null)).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/playback"))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$[0].playback_date").value("01/01/2025"))
               .andExpect(jsonPath("$[0].playback_time").value("00:00:00"))
               .andExpect(jsonPath("$[0].playback_time_zone").value("GMT"))
               .andExpect(jsonPath("$[0].user").value(reportItem.getUser()))
               .andExpect(jsonPath("$[0].user_organisation").value(reportItem.getUserOrganisation()))
               .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
               .andExpect(jsonPath("$[0].court_name").value(reportItem.getCourtName()))
               .andExpect(jsonPath("$[0].county").value(reportItem.getCounty()))
               .andExpect(jsonPath("$[0].postcode").value(reportItem.getPostcode()))
               .andExpect(jsonPath("$[0].regions").value(reportItem.getRegions()));

        verify(reportService, times(1)).reportPlayback(null);
    }

    private PlaybackReportDTO createPlaybackReport(Timestamp createdAt) {

        var user = new User();
        user.setId(UUID.randomUUID());
        user.setOrganisation("FooOrg");
        user.setFirstName("Example");
        user.setLastName("Person");

        var regionEntity = new Region();
        regionEntity.setId(UUID.randomUUID());
        regionEntity.setName("London");

        var regionEntity2 = new Region();
        regionEntity2.setId(UUID.randomUUID());
        regionEntity2.setName("Manchester");

        var courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        courtEntity.setName("Example Court");
        courtEntity.setRegions(Set.of(regionEntity, regionEntity2));
        courtEntity.setCounty("Kent");
        courtEntity.setPostcode("AB1 2CD");

        var recordingEntity = new Recording();
        recordingEntity.setId(UUID.randomUUID());

        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(courtEntity);
        caseEntity.setReference("ABC123");

        var bookingEntity = new Booking();
        bookingEntity.setId(UUID.randomUUID());
        bookingEntity.setCaseId(caseEntity);

        var captureSessionEntity = new CaptureSession();
        captureSessionEntity.setId(UUID.randomUUID());
        captureSessionEntity.setBooking(bookingEntity);

        recordingEntity.setCaptureSession(captureSessionEntity);

        var auditEntity = new Audit();
        auditEntity.setId(UUID.randomUUID());
        auditEntity.setCreatedAt(createdAt);
        auditEntity.setTableRecordId(recordingEntity.getId());

        return new PlaybackReportDTO(auditEntity, user, null);
    }

    private SharedReportDTO createSharedReport() {
        var reportItem = new SharedReportDTO();
        var timestamp = Timestamp.from(Instant.now());
        reportItem.setShareDate(DateTimeUtils.formatDate(timestamp));
        reportItem.setShareTime(DateTimeUtils.formatTime(timestamp));
        reportItem.setTimezone(DateTimeUtils.getTimezoneAbbreviation(timestamp));
        reportItem.setSharedWith("shared-with@example.com");
        reportItem.setSharedWithFullName("Example One");
        reportItem.setGrantedBy("shared-by@example.com");
        reportItem.setGrantedByFullName("Example Two");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setCounty("Example County");
        reportItem.setPostcode("AB1 2CD");
        reportItem.setRegion("Example Region");
        return reportItem;
    }
}
