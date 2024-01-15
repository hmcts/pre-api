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
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
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
}
