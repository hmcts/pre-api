package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.BookingController;
import uk.gov.hmcts.reform.preapi.model.Booking;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
class BookingControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should create a booking with 201 response code")
    @Test
    void createBookingEndpointCreated() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var b = new Booking();
        b.setId(bookingId);
        b.setCaseId(caseId);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(b))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(caseId, bookingId));
    }

    @DisplayName("Should fail to create a booking with 400 response code caseId mismatch")
    @Test
    void createBookingEndpointCaseIdMismatch() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var b = new Booking();
        b.setId(bookingId);
        b.setCaseId(UUID.randomUUID());

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(b))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path caseId does not match payload property booking.caseId\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code bookingId mismatch")
    @Test
    void createBookingEndpointBookingIdMismatch() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();

        var b = new Booking();
        b.setId(UUID.randomUUID());
        b.setCaseId(caseId);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(b))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path bookingId does not match payload property booking.id\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code")
    @Test
    void createBookingEndpointNotAcceptable() throws Exception {
        mockMvc.perform(put(getPath(UUID.randomUUID(), UUID.randomUUID()))).andExpect(status().is4xxClientError());
    }

    private String getPath(UUID caseId, UUID bookingId) {
        return "/cases/" + caseId + "/bookings/" + bookingId;
    }
}
