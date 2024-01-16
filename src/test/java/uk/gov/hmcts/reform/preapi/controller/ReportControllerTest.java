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
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    @DisplayName("Should get a report containing a list of concurrent capture sessions")
    @Test
    void reportConcurrentCaptureSessionsSuccess() throws Exception {
        var reportItem = new CaptureSessionReportDTO();
        reportItem.setId(UUID.randomUUID());
        reportItem.setStartTime(Timestamp.from(Instant.now()));
        reportItem.setEndTime(Timestamp.from(Instant.now()));
        reportItem.setDuration(Duration.ofMinutes(3));
        reportItem.setCourt("Example Court");
        reportItem.setCaseReference("ABC123");

        when(reportService.reportCaptureSessions()).thenReturn(List.of(reportItem));

        mockMvc.perform(get("/reports/capture-sessions-concurrent"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(reportItem.getId().toString()));
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
        reportItem.setAllocatedBy("example2@example.com");
        reportItem.setCaseReference("ABC123");
        reportItem.setCourt("Example Court");
        reportItem.setRegions(Set.of());
        reportItem.setBookingId(UUID.randomUUID());

        when(reportService.reportShared()).thenReturn(List.of(reportItem));
        mockMvc.perform(get("/reports/shared-bookings"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].allocated_to").value(reportItem.getAllocatedTo()))
            .andExpect(jsonPath("$[0].allocated_by").value(reportItem.getAllocatedBy()))
            .andExpect(jsonPath("$[0].case_reference").value(reportItem.getCaseReference()))
            .andExpect(jsonPath("$[0].court").value(reportItem.getCourt()))
            .andExpect(jsonPath("$[0].booking_id").value(reportItem.getBookingId().toString()));
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
}
