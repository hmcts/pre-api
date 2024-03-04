package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.CaptureSessionController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.RecordingNotDeletedException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    private static final String TEST_URL = "http://localhost";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String CAPTURE_SESSION_ID_PATH = "/capture-sessions/{id}";

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

        when(captureSessionService.searchBy(any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), isNull(), isNull(), eq(Optional.empty()), any(), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by case reference")
    @Test
    void getAllCaptureSessionsFilterCaseReferenceSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = "TEST";

        when(captureSessionService.searchBy(eq(searchParam), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("caseReference", searchParam))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(eq(searchParam), isNull(), isNull(), isNull(), eq(Optional.empty()), any(), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by booking id")
    @Test
    void getAllCaptureSessionsFilterBookingSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = UUID.randomUUID();

        when(captureSessionService.searchBy(any(), eq(searchParam), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("bookingId", searchParam.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), eq(searchParam), isNull(), isNull(), eq(Optional.empty()), any(), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by origin")
    @Test
    void getAllCaptureSessionsFilterOriginSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = RecordingOrigin.PRE;

        when(captureSessionService.searchBy(any(), any(), eq(searchParam), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("origin", searchParam.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), eq(searchParam), isNull(), eq(Optional.empty()), any(), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by recording status")
    @Test
    void getAllCaptureSessionsFilterStatusSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = RecordingStatus.RECORDING_AVAILABLE;

        when(captureSessionService.searchBy(any(), any(), any(), eq(searchParam), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(mock)));

        mockMvc.perform(get("/capture-sessions")
                            .param("recordingStatus", searchParam.name()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.captureSessionDTOList[0].id").value(mock.getId().toString()));

        verify(captureSessionService, times(1))
            .searchBy(isNull(), isNull(), isNull(), eq(searchParam), eq(Optional.empty()), any(), any());
    }

    @DisplayName("Should get a list of capture sessions with 200 response code filtered by scheduled for")
    @Test
    void getAllCaptureSessionsFilterScheduledForSuccess() throws Exception {
        var mock = new CaptureSessionDTO();
        mock.setId(UUID.randomUUID());
        var searchParam = "2023-01-01";

        when(captureSessionService.searchBy(any(), any(), any(), any(), any(), any(), any()))
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
                isNull(),
                any()
            );
    }

    @DisplayName("Should delete capture session with 200 response code")
    @Test
    void deleteCaptureSessionByIdSuccess() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(delete(CAPTURE_SESSION_ID_PATH, id)
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should delete capture session with 404 response code when not found")
    @Test
    void deleteCaptureSessionByIdNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new NotFoundException("CaptureSession: " + id)).when(captureSessionService).deleteById(id);

        mockMvc.perform(delete(CAPTURE_SESSION_ID_PATH, id)
                            .with(csrf()))
            .andExpect(status().isNotFound());
    }

    @DisplayName("Should return 400 when booking has associated recordings that have not been deleted")
    @Test
    void deleteCaptureSessionRecordingNotDeleted() throws Exception {
        var captureSessionId = UUID.randomUUID();
        doThrow(new RecordingNotDeletedException()).when(captureSessionService).deleteById(captureSessionId);

        mockMvc.perform(delete("/capture-sessions/" + captureSessionId)
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Cannot delete because and associated recording has not been deleted."));

    }

    @DisplayName("Should create capture session with 201 response code")
    @Test
    void createCaptureSessionSuccess() throws Exception {
        var id = UUID.randomUUID();
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(id);
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.upsert(dto)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, id)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(dto))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/capture-sessions/" + id);
    }

    @DisplayName("Should update capture session with 204 response code")
    @Test
    void updateCaptureSessionSuccess() throws Exception {
        var id = UUID.randomUUID();
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(id);
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.upsert(dto)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, id)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(dto))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/capture-sessions/" + id);
    }

    @DisplayName("Should fail create/update capture session with 400 error when id is null")
    @Test
    void createCaptureSessionIdNullBadRequest() throws Exception {
        var dto =  new CreateCaptureSessionDTO();
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(dto))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("{\"id\":\"id is required\"}");
    }

    @DisplayName("Should fail create/update capture session with 400 error when booking id is null")
    @Test
    void createCaptureSessionBookingIdBadRequest() throws Exception {
        var id = UUID.randomUUID();
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(id);
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, id)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(dto))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("{\"bookingId\":\"booking_id is required\"}");
    }

    @DisplayName("Should fail create/update capture session with 400 error when origin is null")
    @Test
    void createCaptureSessionOriginBadRequest() throws Exception {
        var id = UUID.randomUUID();
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(id);
        dto.setBookingId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, id)
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(dto))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("{\"origin\":\"origin is required\"}");
    }

    @DisplayName("Should fail create/update capture session with 400 error when status is null")
    @Test
    void createCaptureSessionStatusBadRequest() throws Exception {
        var id = UUID.randomUUID();
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(id);
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);

        var response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, id)
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(dto))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("{\"status\":\"status is required\"}");
    }

    @DisplayName("Should fail create/update capture session with 400 error when path and dto ids do not match")
    @Test
    void createCaptureSessionPayloadMismatch() throws Exception {
        var dto =  new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(UUID.randomUUID());
        dto.setOrigin(RecordingOrigin.PRE);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var response = mockMvc.perform(put(CAPTURE_SESSION_ID_PATH, UUID.randomUUID())
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(dto))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path id does not match payload property createCaptureSessionDTO.id\"}");
    }

    @DisplayName("Should undelete a capture session by id and return a 200 response")
    @Test
    void undeleteCaptureSessionSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        doNothing().when(captureSessionService).undelete(captureSessionId);

        mockMvc.perform(post("/capture-sessions/" + captureSessionId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should undelete a capture session by id and return a 404 response")
    @Test
    void undeleteCaptureSessionNotFound() throws Exception {
        var captureSessionId = UUID.randomUUID();
        doThrow(
            new NotFoundException("Capture Session: " + captureSessionId)
        ).when(captureSessionService).undelete(captureSessionId);

        mockMvc.perform(post("/capture-sessions/" + captureSessionId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Capture Session: " + captureSessionId));
    }
}
