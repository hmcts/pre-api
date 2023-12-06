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
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
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
class RecordingDTOControllerTest {
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
        RecordingDTO mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(recordingId);
        when(recordingService.findById(bookingId, recordingId)).thenReturn(mockRecordingDTO);

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
            .andExpect(jsonPath("$.message").value("Not found: RecordingDTO: " + recordingId));
    }

    @DisplayName("Should return 404 when trying to get a non-existing booking")
    @Test
    void testGetNonExistingBookingId() throws Exception {
        UUID recordingId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        when(recordingService.findById(bookingId, recordingId)).thenThrow(
            new NotFoundException("BookingDTO: " + bookingId)
        );

        mockMvc.perform(get(getPath(bookingId.toString(), recordingId.toString())))
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
        RecordingDTO mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(recordingId);
        List<RecordingDTO> recordingDTOList = List.of(mockRecordingDTO);
        when(recordingService.findAllByBookingId(bookingId)).thenReturn(recordingDTOList);

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
        doThrow(new NotFoundException("BookingDTO: " + bookingId)).when(recordingService).findAllByBookingId(any());

        mockMvc.perform(get("/bookings/" + bookingId + "/recordings"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: BookingDTO: " + bookingId));
    }

    private static String getPath(String bookingId, String recordingId) {
        return "/bookings/" + bookingId + "/recordings/" + recordingId;
    }
}
