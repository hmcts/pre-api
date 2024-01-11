package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.BookingController;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.util.ArrayList;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @DisplayName("Should search for bookings")
    @Test
    void searchBookingsOk() throws Exception {
        var caseDTO1 = new CaseDTO();
        caseDTO1.setId(UUID.randomUUID());
        caseDTO1.setReference("MyRef1");
        var caseDTO2 = new CaseDTO();
        caseDTO2.setId(UUID.randomUUID());
        caseDTO2.setReference("MyRef2");
        var booking1 = new BookingDTO();
        booking1.setId(UUID.randomUUID());
        booking1.setCaseDTO(caseDTO1);
        var booking2 = new BookingDTO();
        booking2.setId(UUID.randomUUID());
        booking2.setCaseDTO(caseDTO2);

        when(bookingService.searchBy(any(), eq("MyRef"), any())).thenReturn(new PageImpl<>(new ArrayList<>() {
            {
                add(booking1);
                add(booking2);
            }
        }));

        MvcResult response = mockMvc.perform(get("/bookings?caseReference=MyRef")
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        var rootNode = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(rootNode.get("_embedded").get("bookingDTOList").get(0).get("id").toString())
            .isEqualTo('"' + booking1.getId().toString() + '"');
        assertThat(rootNode.get("_embedded").get("bookingDTOList").get(1).get("id").toString())
            .isEqualTo('"' + booking2.getId().toString() + '"');
    }

    @DisplayName("Should get all bookings for a case with 200 response code")
    @Test
    void getAllBookingsForCaseIDEndpointOk() throws Exception {
        var caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        var booking1 = new BookingDTO();
        booking1.setId(UUID.randomUUID());
        booking1.setCaseDTO(caseDTO);
        var booking2 = new BookingDTO();
        booking2.setId(UUID.randomUUID());
        booking2.setCaseDTO(caseDTO);

        when(bookingService.searchBy(eq(caseDTO.getId()), any(), any())).thenReturn(new PageImpl<>(new ArrayList<>() {
            {
                add(booking1);
                add(booking2);
            }
        }));

        MvcResult response = mockMvc.perform(get("/bookings?caseId=" + caseDTO.getId())
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        var rootNode = mapper.readTree(response.getResponse().getContentAsString());
        assertThat(rootNode.get("page").get("totalElements").asInt()).isEqualTo(2);
        assertThat(rootNode.get("_embedded").get("bookingDTOList").get(0).get("id").toString())
            .isEqualTo('"' + booking1.getId().toString() + '"');
        assertThat(rootNode.get("_embedded").get("bookingDTOList").get(1).get("id").toString())
            .isEqualTo('"' + booking2.getId().toString() + '"');
    }

    @DisplayName("Requesting a page out of bounds")
    @Test
    void getOutOfBoundsPageError() throws Exception {
        var caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());
        var booking1 = new BookingDTO();
        booking1.setId(UUID.randomUUID());
        booking1.setCaseDTO(caseDTO);
        var booking2 = new BookingDTO();
        booking2.setId(UUID.randomUUID());
        booking2.setCaseDTO(caseDTO);
        when(bookingService.searchBy(any(), any(), any())).thenReturn(new PageImpl<>(new ArrayList<>() {
            {
                add(booking1);
                add(booking2);
            }
        }));

        MvcResult response = mockMvc.perform(get("/bookings")
                                                 .param("page", "2")
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();


        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Requested page {2} is out of range. Max page is {1}\"}");
    }

    @DisplayName("Should get all bookings for a case with 200 response code even when no bookings attached to case")
    @Test
    void getAllBookingsForCaseIdEndpointEmptyOk() throws Exception {
        var caseDTO = new CaseDTO();
        caseDTO.setId(UUID.randomUUID());

        when(bookingService.searchBy(eq(caseDTO.getId()), any(), any())).thenReturn(Page.empty());

        MvcResult response = mockMvc.perform(get("/bookings?caseId=" + caseDTO.getId())
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        var rootNode = mapper.readTree(response.getResponse().getContentAsString());

        assertThat(rootNode.get("page").get("totalElements").asInt()).isEqualTo(0);
    }

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

        MvcResult response = mockMvc.perform(get(getPath(bookingId))
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo(OBJECT_MAPPER.writeValueAsString(booking));
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

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(bookingId)
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

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(bookingId)
        );
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

        MvcResult response = mockMvc.perform(put(getPath(UUID.randomUUID()))
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

        mockMvc.perform(put(getPath(UUID.randomUUID()))).andExpect(status().is4xxClientError());
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

        mockMvc.perform(put(getPath(bookingId))
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

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
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


        mockMvc.perform(delete(getPath(bookingId))
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent());

    }

    @DisplayName("Should create a share booking with 201 response code")
    @Test
    void testShareBookingCreated() throws Exception {
        final UUID shareBookingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        final UUID sharedWithUserId = UUID.randomUUID();
        final UUID sharedByUserId = UUID.randomUUID();
        final UUID caseId = UUID.randomUUID();

        var shareBooking = new ShareBookingDTO();
        shareBooking.setId(shareBookingId);
        shareBooking.setSharedWithUserId(sharedWithUserId);
        shareBooking.setSharedByUserId(sharedByUserId);
        shareBooking.setBookingId(bookingId);

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setId(caseId);

        var mockBookingDTO = new BookingDTO();
        mockBookingDTO.setId(bookingId);

        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.findById(bookingId)).thenReturn(mockBookingDTO);

        MvcResult response = mockMvc.perform(put(getPath(bookingId) + "/share")
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(shareBooking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
                                                 .andExpect(status().isCreated())
                                                 .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");

        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(bookingId)
            + "/share"
        );
    }

    @DisplayName("Should fail to create a share booking with 400 response code when the bookingId does not match")
    @Test
    void testShareBookingIdMismatch() throws Exception {
        final UUID shareBookingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();
        final UUID sharedWithUserId = UUID.randomUUID();
        final UUID sharedByUserId = UUID.randomUUID();
        final UUID caseId = UUID.randomUUID();

        var shareRecording = new ShareBookingDTO();
        shareRecording.setId(shareBookingId);
        shareRecording.setBookingId(UUID.randomUUID());
        shareRecording.setSharedWithUserId(sharedWithUserId);
        shareRecording.setSharedByUserId(sharedByUserId);

        var mockCaseDTO = new CaseDTO();
        mockCaseDTO.setId(caseId);

        var mockBookingDTO = new BookingDTO();
        mockBookingDTO.setId(bookingId);

        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.findById(bookingId)).thenReturn(mockBookingDTO);

        MvcResult response = mockMvc.perform(put(getPath(bookingId) + "/share")
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(shareRecording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
                                                 .andExpect(status().is4xxClientError())
                                                 .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path bookingId does not match payload "
                           + "property shareBookingDTO.bookingId\"}");
    }

    private String getPath(UUID bookingId) {
        return "/bookings/" + bookingId;
    }
}
