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
import uk.gov.hmcts.reform.preapi.controllers.BookingController;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.model.Booking;
import uk.gov.hmcts.reform.preapi.model.Case;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"PMD.LinguisticNaming"})
class BookingControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    @MockBean
    private BookingService bookingService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get a booking with 200 response code")
    @Test
    void getBookingEndpointOk() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        Case mockCase = new Case();
        mockCase.setId(caseId);
        when(caseService.findById(caseId)).thenReturn(mockCase);
        when(bookingService.findById(bookingId)).thenReturn(booking);

        MvcResult response = mockMvc.perform(get(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(booking));
    }

    @DisplayName("Should get a booking with 404 response code as case not found")
    @Test
    void getBookingEndpointCaseNotFound() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        when(caseService.findById(caseId)).thenReturn(null);
        when(bookingService.findById(bookingId)).thenReturn(booking);

        MvcResult response = mockMvc.perform(get(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(
            response.getResponse().getContentAsString()).isEqualTo("{\"message\":\"Not found: Case " + caseId + "\"}"
        );
    }

    @DisplayName("Should create a booking with 201 response code")
    @Test
    void createBookingEndpointCreated() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        Case mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);
        when(bookingService.upsert(booking)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(caseId, bookingId)
        );
    }

    @DisplayName("Should update a booking with 204 response code")
    @Test
    void createBookingEndpointUpdate() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        Case mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);
        when(bookingService.upsert(booking)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(caseId, bookingId)
        );
    }

    @DisplayName("Should fail to create a booking with 400 response code caseId mismatch")
    @Test
    void createBookingEndpointCaseIdMismatch() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(UUID.randomUUID());

        var mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
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

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseId);

        var mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        MvcResult response = mockMvc.perform(put(getPath(caseId, UUID.randomUUID()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
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

        var caseId = UUID.randomUUID();
        var mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        mockMvc.perform(put(getPath(caseId, UUID.randomUUID()))).andExpect(status().is4xxClientError());
    }

    @DisplayName("Should fail to find case with 404 response code")
    @Test
    void createBookingEndpointCaseNotFound() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        when(caseService.findById(caseId)).thenReturn(null);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Case " + caseId + "\"}");
    }

    @DisplayName("Should fail to create a booking with 500 response code")
    @Test
    void createBookingEndpoint500() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new Booking();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        Case mockCase = new Case();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        mockMvc.perform(put(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is5xxServerError());
    }

    private String getPath(UUID caseId, UUID bookingId) {
        return "/cases/" + caseId + "/bookings/" + bookingId;
    }
}
