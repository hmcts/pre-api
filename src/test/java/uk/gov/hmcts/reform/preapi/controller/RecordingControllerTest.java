package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.RecordingController;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(RecordingController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class RecordingControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private RecordingService recordingService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";


    @DisplayName("Should get recording by ID with 200 response code")
    @Test
    void testGetRecordingByIdSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        var mockCreateRecordingDTO = new CreateRecordingDTO();
        mockCreateRecordingDTO.setId(recordingId);
        when(recordingService.findById(bookingId, recordingId)).thenReturn(mockCreateRecordingDTO);

        mockMvc.perform(get(getPath(bookingId, recordingId)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(recordingId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing recording")
    @Test
    void testGetNonExistingRecordingById() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(recordingService.findById(bookingId, recordingId)).thenReturn(null);

        mockMvc.perform(get(getPath(bookingId, recordingId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: CreateRecordingDTO: " + recordingId));
    }

    @DisplayName("Should return 404 when trying to get a non-existing booking")
    @Test
    void testGetNonExistingBookingId() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(recordingService.findById(bookingId, recordingId)).thenThrow(
            new NotFoundException("BookingDTO: " + bookingId)
        );

        mockMvc.perform(get(getPath(bookingId, recordingId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: BookingDTO: " + bookingId));
    }

    // TODO 200 response
    @DisplayName("Should get a list of recordings by booking id with 200 response code")
    @Test
    void testGetRecordingByBookingIdSuccess() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        CreateRecordingDTO mockCreateRecordingDTO = new CreateRecordingDTO();
        mockCreateRecordingDTO.setId(recordingId);
        List<CreateRecordingDTO> createRecordingDTOList = List.of(mockCreateRecordingDTO);
        when(recordingService.findAllByBookingId(bookingId, null, null)).thenReturn(createRecordingDTOList);

        mockMvc.perform(get("/bookings/" + bookingId + "/recordings"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isNotEmpty())
            .andExpect(jsonPath("$[0].id").value(recordingId.toString()));
    }

    @DisplayName("Should return 404 when trying to get recordings for a booking that doesn't exist")
    @Test
    void testGetRecordingsBookingNotFound() throws Exception {
        UUID bookingId = UUID.randomUUID();
        doThrow(new NotFoundException("BookingDTO: " + bookingId))
            .when(recordingService)
            .findAllByBookingId(any(), any(), any());

        mockMvc.perform(get("/bookings/" + bookingId + "/recordings"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: BookingDTO: " + bookingId));
    }

    @DisplayName("Should create a recording with 201 response code")
    @Test
    void createRecordingCreated() throws Exception {
        var bookingId = UUID.randomUUID();
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);

        when(recordingService.upsert(bookingId, recording)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(bookingId, recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(bookingId, recordingId)
        );
    }

    @DisplayName("Should update a recording with 204 response code")
    @Test
    void updateRecordingUpdate() throws Exception {
        var bookingId = UUID.randomUUID();
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);

        when(recordingService.upsert(bookingId, recording)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(bookingId, recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(bookingId, recordingId)
        );
    }

    @DisplayName("Should fail to create/update a recording with 400 response code recordingId mismatch")
    @Test
    void createRecordingIdMismatch() throws Exception {
        var bookingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(UUID.randomUUID());

        MvcResult response = mockMvc.perform(put(getPath(bookingId, UUID.randomUUID()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path recordingId does not match payload property createRecordingDTO.id\"}");
    }

    @DisplayName("Should fail to create/update a recording with 404 response code when booking does not exist")
    @Test
    void createRecordingBookingNotFound() throws Exception {
        var bookingId = UUID.randomUUID();
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);

        doThrow(new NotFoundException("Booking: " + bookingId)).when(recordingService).upsert(any(), any());

        MvcResult response = mockMvc.perform(put(getPath(bookingId, recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Booking: " + bookingId + "\"}");
    }

    @DisplayName("Should fail to create/update a recording with 404 response code when capture session does not exist")
    @Test
    void createRecordingCaptureSessionNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        var captureSessionId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(captureSessionId);
        var bookingId = UUID.randomUUID();

        doThrow(new NotFoundException("Capture Session: " + captureSessionId))
            .when(recordingService)
            .upsert(any(), any());

        MvcResult response = mockMvc.perform(put(getPath(bookingId, recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Capture Session: " + captureSessionId + "\"}");
    }

    @DisplayName("Should fail to create/update a recording with 404 response code when parent recording does not exist")
    @Test
    void createRecordingParentRecordingNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        var parentRecordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setParentRecordingId(parentRecordingId);
        var bookingId = UUID.randomUUID();

        doThrow(new NotFoundException("Recording: " + parentRecordingId))
            .when(recordingService)
            .upsert(any(), any());

        MvcResult response = mockMvc.perform(put(getPath(bookingId, recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Recording: " + parentRecordingId + "\"}");
    }

    @DisplayName("Should delete recording with 200 response code")
    @Test
    void testDeleteRecordingSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        doNothing().when(recordingService).deleteById(bookingId, recordingId);

        mockMvc.perform(delete(getPath(bookingId, recordingId))
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should return 404 when booking doesn't exist")
    @Test
    void testDeleteRecordingBookingNotFound() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        doThrow(new NotFoundException("Booking: " + bookingId))
            .when(recordingService)
            .deleteById(bookingId, recordingId);

        mockMvc.perform(delete(getPath(bookingId, recordingId))
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Booking: " + bookingId));
    }

    @DisplayName("Should return 404 when recording doesn't exist")
    @Test
    void testDeleteRecordingNotFound() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        doThrow(new NotFoundException("Recording: " + recordingId))
            .when(recordingService)
            .deleteById(bookingId, recordingId);

        mockMvc.perform(delete(getPath(bookingId, recordingId))
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Recording: " + recordingId));
    }

    private static String getPath(UUID bookingId, UUID recordingId) {
        return "/bookings/" + bookingId + "/recordings/" + recordingId;
    }
}
