package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.CaptureSessionController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptureSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CaptureSessionControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private CaptureSessionService captureSessionService;
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get capture session by id with 200 response code")
    @Test
    void getCaptureSessionByIdSuccess() throws Exception {
        var id = UUID.randomUUID();
        var model = new CaptureSessionDTO();
        model.setId(id);
        when(captureSessionService.findById(id)).thenReturn(model);

        mockMvc.perform(get(TEST_URL + "/capture-sessions/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @DisplayName("Should return 404 when trying to get a non-existing capture session")
    @Test
    void getCaptureSessionByIdNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new NotFoundException("CaptureSession: " + id)).when(captureSessionService).findById(id);

        mockMvc.perform(get(TEST_URL + "/capture-sessions/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: CaptureSession: " + id));
    }

    @DisplayName("Should get a list of capture sessions with 200 response code")
    @Test
    void getAllCaptureSessionsSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());

        when(captureSessionService.searchBy(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), isNull(), isNull(), eq(Optional.empty()), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by case reference")
    @Test
    void getAllCaptureSessionsFilterCaseReferenceSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = "TEST";

        when(captureSessionService.searchBy(eq(searchParam), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("caseReference", searchParam))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(eq(searchParam), isNull(), isNull(), isNull(), eq(Optional.empty()), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by booking id")
    @Test
    void getAllCaptureSessionsFilterBookingSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = UUID.randomUUID();

        when(captureSessionService.searchBy(any(), eq(searchParam), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("bookingId", searchParam.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), eq(searchParam), isNull(), isNull(), eq(Optional.empty()), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by origin")
    @Test
    void getAllCaptureSessionsFilterOriginSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = RecordingOrigin.PRE;

        when(captureSessionService.searchBy(any(), any(), eq(searchParam), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("origin", searchParam.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), eq(searchParam), isNull(), eq(Optional.empty()), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by recording status")
    @Test
    void getAllCaptureSessionsFilterStatusSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = RecordingStatus.AVAILABLE;

        when(captureSessionService.searchBy(any(), any(), any(), eq(searchParam), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("recordingStatus", searchParam.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), isNull(), eq(searchParam), eq(Optional.empty()), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by scheduled for")
    @Test
    void getAllCaptureSessionsFilterScheduledForSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = "2023-01-01";

        when(captureSessionService.searchBy(any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("scheduledFor", searchParam))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(
                isNull(),
                isNull(),
                isNull(),
                isNull(),
                eq(Optional.of(Timestamp.valueOf("2023-01-01 00:00:00"))),
                any()
            );
    }
}
