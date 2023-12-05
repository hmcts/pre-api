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
import uk.gov.hmcts.reform.preapi.controllers.RecordingController;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Recording;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        Recording mockRecording = new Recording();
        mockRecording.setId(recordingId);
        when(recordingService.findById(bookingId, recordingId)).thenReturn(mockRecording);

        mockMvc.perform(get(getPath(bookingId.toString(), recordingId.toString())))
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

        mockMvc.perform(get(getPath(bookingId.toString(), recordingId.toString())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Recording: " + recordingId));
    }

    @DisplayName("Should return 404 when trying to get a non-existing booking")
    @Test
    void testGetNonExistingBookingId() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(recordingService.findById(bookingId, recordingId)).thenThrow(
            new NotFoundException("Booking: " + bookingId)
        );

        mockMvc.perform(get(getPath(bookingId.toString(), recordingId.toString())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Booking: " + bookingId));
    }

    // TODO 200 response
    @DisplayName("Should get a list of recordings by booking id with 200 response code")
    @Test
    void testGetRecordingByBookingIdSuccess() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID recordingId = UUID.randomUUID();
        Recording mockRecording = new Recording();
        mockRecording.setId(recordingId);
        List<Recording> recordingList = List.of(mockRecording);
        when(recordingService.findAllByBookingId(bookingId)).thenReturn(recordingList);

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
        doThrow(new NotFoundException("Booking: " + bookingId)).when(recordingService).findAllByBookingId(any());

        mockMvc.perform(get("/bookings/" + bookingId + "/recordings"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Booking: " + bookingId));
    }

    private static String getPath(String bookingId, String recordingId) {
        return "/bookings/" + bookingId + "/recordings/" + recordingId;
    }
}
