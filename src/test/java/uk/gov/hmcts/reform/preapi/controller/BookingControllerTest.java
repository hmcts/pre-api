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
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

        var caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        var bookingId = UUID.randomUUID();
        var booking = new BookingDTO();
        booking.setId(bookingId);
        booking.setCaseDTO(caseDTO);

        when(caseService.findById(caseDTO.getId())).thenReturn(caseDTO);
        when(bookingService.findById(bookingId)).thenReturn(booking);

        MvcResult response = mockMvc.perform(get(getPath(caseDTO.getId(), bookingId))
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(booking));
    }

    @DisplayName("Should get a booking with 404 response code as case not found")
    @Test
    void getBookingEndpointCaseNotFound() throws Exception {

        var caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        var bookingId = UUID.randomUUID();
        var booking = new BookingDTO();
        booking.setId(bookingId);
        booking.setCaseDTO(caseDTO);

        when(caseService.findById(caseDTO.getId())).thenReturn(null);
        when(bookingService.findById(bookingId)).thenReturn(booking);

        MvcResult response = mockMvc.perform(get(getPath(caseDTO.getId(), bookingId))
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: CaseDTO " + caseDTO.getId() + "\"}");
    }

    @DisplayName("Should create a booking with 201 response code")
    @Test
    void createBookingEndpointCreated() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
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
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
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
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(UUID.randomUUID());

        var mockCase = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path caseId does not match payload property bookingDTO.caseId\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code bookingId mismatch")
    @Test
    void createBookingEndpointBookingIdMismatch() throws Exception {

        var caseId = UUID.randomUUID();

        var booking = new CreateBookingDTO();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseId);

        var mockCase = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        MvcResult response = mockMvc.perform(put(getPath(caseId, UUID.randomUUID()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path bookingId does not match payload property bookingDTO.id\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code")
    @Test
    void createBookingEndpointNotAcceptable() throws Exception {

        var caseId = UUID.randomUUID();
        var mockCase = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCase);

        mockMvc.perform(put(getPath(caseId, UUID.randomUUID()))).andExpect(status().is4xxClientError());
    }

    @DisplayName("Should fail to find case with 404 response code")
    @Test
    void createBookingEndpointCaseNotFound() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
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
            .isEqualTo("{\"message\":\"Not found: CaseDTO " + caseId + "\"}");
    }

    @DisplayName("Should fail to create a booking with 500 response code")
    @Test
    void createBookingEndpoint500() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);

        mockMvc.perform(put(getPath(caseId, bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is5xxServerError());
    }

    @DisplayName("Should fail to update a booking with 400 response code as its already deleted")
    @Test
    void updateBookingEndpoint400() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(booking))
            .thenThrow(new ResourceInDeletedStateException("BookingDTO", bookingId.toString()));

        MvcResult response = mockMvc.perform(put(getPath(caseId, bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"message\":\"Resource BookingDTO(" + bookingId + ") is in a deleted state and cannot be updated\"}"
            );
    }


    @DisplayName("Should delete a booking with 202 response code")
    @Test
    void deleteBookingEndpoint202() throws Exception {
        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);


        mockMvc.perform(delete(getPath(caseId, bookingId))
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent());

    }

    private String getPath(UUID caseId, UUID bookingId) {
        return "/cases/" + caseId + "/bookings/" + bookingId;
    }
}
