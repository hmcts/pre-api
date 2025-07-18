package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.BookingController;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.CaptureSessionNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.BookingService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.ShareBookingService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static java.util.Collections.emptySet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"PMD.LinguisticNaming"})
class BookingControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private ShareBookingService shareBookingService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String TEST_URL = "http://localhost";

    @BeforeAll
    static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

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

        when(bookingService.searchBy(any(), eq("MyRef"), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(booking1, booking2)));

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

        when(bookingService.searchBy(eq(caseDTO.getId()), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(booking1, booking2)));


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
        when(bookingService.searchBy(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(List.of(booking1, booking2)));


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

        when(bookingService.searchBy(eq(caseDTO.getId()), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        MvcResult response = mockMvc.perform(get("/bookings?caseId=" + caseDTO.getId())
                                                 .with(csrf())
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ObjectMapper mapper = new ObjectMapper();
        var rootNode = mapper.readTree(response.getResponse().getContentAsString());

        assertThat(rootNode.get("page").get("totalElements").asInt()).isEqualTo(0);
    }

    @DisplayName("Should get all bookings with a capture session one of the listed statuses")
    @Test
    void searchBookingsByStatusOk() throws Exception {
        when(bookingService.searchBy(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/bookings?captureSessionStatusIn=RECORDING,NO_RECORDING"))
            .andExpect(status().isOk());

        verify(bookingService, times(1))
            .searchBy(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(List.of(RecordingStatus.RECORDING, RecordingStatus.NO_RECORDING)),
                any(),
                any()
            );
    }

    @DisplayName("Should get all bookings with a capture session one of the listed statuses")
    @Test
    void searchBookingsByNotStatusOk() throws Exception {
        when(bookingService.searchBy(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(Page.empty());

        mockMvc.perform(get("/bookings?captureSessionStatusNotIn=RECORDING,NO_RECORDING"))
            .andExpect(status().isOk());

        verify(bookingService, times(1))
            .searchBy(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                eq(List.of(RecordingStatus.RECORDING, RecordingStatus.NO_RECORDING)),
                any()
            );
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
        var shareBooking = new ShareBookingDTO();
        shareBooking.setBookingId(bookingId);
        shareBooking.setId(UUID.randomUUID());
        shareBooking.setSharedByUser(HelperFactory.easyCreateBaseUserDTO());
        shareBooking.setSharedWithUser(HelperFactory.easyCreateBaseUserDTO());
        booking.setShares(List.of(shareBooking));

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
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);

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
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.UPDATED);

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
        booking.setScheduledFor(Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant()));
        booking.setParticipants(getCreateParticipantDTOs());

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

    @DisplayName("Should fail to create a booking with 400 response code as scheduledFor is not supplied")
    @Test
    void createBookingEndpoint400ScheduledForMissing() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
                                    .andExpect(status().is4xxClientError())
                                    .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"scheduledFor\":\"scheduled_for is required and must not be before today\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code as scheduledFor is before today")
    @Test
    void createBookingEndpoint400ScheduledForInThePast() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setParticipants(getCreateParticipantDTOs());
        booking.setScheduledFor(Timestamp.from(OffsetDateTime.now().minusWeeks(1).toInstant()));

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
                                    .andExpect(status().isBadRequest())
                                    .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"scheduledFor\":\"must not be before today\"}"
            );
    }

    @DisplayName("Should fail to create a booking with 400 response code as scheduledFor is in the past same day")
    @Test
    void createBookingEndpointScheduledForInThePastButToday() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setParticipants(getCreateParticipantDTOs());
        booking.setScheduledFor(
            Timestamp.from(Instant.now().truncatedTo(ChronoUnit.DAYS))
        );
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(any(CreateBookingDTO.class))).thenReturn(UpsertResult.CREATED);

        mockMvc.perform(put(getPath(bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isCreated());
    }

    @DisplayName("Should fail to update a booking with 400 response code as its already deleted")
    @Test
    void updateBookingEndpoint400() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(any(CreateBookingDTO.class)))
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

    @DisplayName("Should return 400 when booking has associated recordings that have not been deleted")
    @Test
    void deleteBookingRecordingNotDeleted() throws Exception {
        var bookingId = UUID.randomUUID();
        doThrow(new CaptureSessionNotDeletedException()).when(bookingService).markAsDeleted(bookingId);

        mockMvc.perform(delete("/bookings/" + bookingId)
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Cannot delete because and associated recording has not been deleted."));

    }

    @DisplayName("Should create a share booking with 201 response code")
    @Test
    void testShareBookingCreated() throws Exception {
        final UUID shareBookingId = UUID.randomUUID();
        final UUID bookingId = UUID.randomUUID();

        var shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(shareBookingId);
        shareBooking.setSharedWithUser(UUID.randomUUID());
        shareBooking.setSharedByUser(UUID.randomUUID());
        shareBooking.setBookingId(bookingId);

        when(shareBookingService.shareBookingById(any())).thenReturn(UpsertResult.CREATED);

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

        var shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(shareBookingId);
        shareBooking.setBookingId(UUID.randomUUID());
        shareBooking.setSharedWithUser(UUID.randomUUID());
        shareBooking.setSharedByUser(UUID.randomUUID());

        MvcResult response = mockMvc.perform(put(getPath(bookingId) + "/share")
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(shareBooking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
                                                 .andExpect(status().is4xxClientError())
                                                 .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path bookingId does not match payload "
                           + "property shareBookingDTO.bookingId\"}");
    }

    @DisplayName("Should create a share booking with 200 response code when shared by is null but the user logged in")
    @Test
    void shareBookingSharedByNullSuccess() throws Exception {
        var shareBookingId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();

        var shareBooking = new CreateShareBookingDTO();
        shareBooking.setId(shareBookingId);
        shareBooking.setBookingId(bookingId);
        shareBooking.setSharedWithUser(UUID.randomUUID());
        shareBooking.setSharedByUser(null);

        when(shareBookingService.shareBookingById(any())).thenReturn(UpsertResult.CREATED);

        var userId = UUID.randomUUID();
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.getUserId()).thenReturn(userId);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

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

        verify(mockAuth, times(1)).getUserId();
    }

    @DisplayName("Should fail to create a booking with 500 response code")
    @Test
    void createBookingEndpoint500() throws Exception {
        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(getCreateParticipantDTOs());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(booking)).thenThrow(new UnknownServerException("Test exception"));

        mockMvc.perform(put(getPath(bookingId))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(booking))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is5xxServerError());
    }

    @DisplayName("Should return 400 when booking id is not a uuid")
    @Test
    void testFindByIdBadRequest() throws Exception {
        mockMvc.perform(get("/bookings/12345678")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid UUID string: 12345678"));
    }

    @DisplayName("Should fail to create a booking without any participants")
    @Test
    void createBookingWithoutParticipants() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(booking)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"participants\":\"Participants must consist of at least 1 defendant and 1 witness\"}");
    }

    @DisplayName("Should fail to create a booking empty participants list")
    @Test
    void createBookingWithZeroParticipants() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(emptySet());

        CaseDTO mockCaseDTO = new CaseDTO();
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);
        when(bookingService.upsert(booking)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"participants\":\"Participants must consist of at least 1 defendant and 1 witness\"}");
    }

    @DisplayName("Should fail to create a booking with 400 response code as only witness supplied")
    @Test
    void createBookingOnlyWitnessSupplied() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(
            Set.of(HelperFactory.createParticipantDTO("John", "Smith", ParticipantType.WITNESS)));

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"participants\":\"Participants must consist of at least 1 defendant and 1 witness\"}"
            );
    }

    @DisplayName("Should fail to create a booking with 400 response code as only defendant supplied")
    @Test
    void createBookingOnlyDefendantSupplied() throws Exception {

        var caseId = UUID.randomUUID();
        var bookingId = UUID.randomUUID();
        var booking = new CreateBookingDTO();
        booking.setId(bookingId);
        booking.setCaseId(caseId);
        booking.setScheduledFor(
            Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant().truncatedTo(ChronoUnit.SECONDS))
        );
        booking.setParticipants(
            Set.of(HelperFactory.createParticipantDTO("John", "Smith", ParticipantType.DEFENDANT)));

        MvcResult response = mockMvc.perform(put(getPath(bookingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(booking))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"participants\":\"Participants must consist of at least 1 defendant and 1 witness\"}"
            );
    }

    @DisplayName("Should get log of all shares for a booking return a page with code 200")
    @Test
    void getShareLogsSuccess() throws Exception {
        var model = new ShareBookingDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(UUID.randomUUID());

        when(shareBookingService.getShareLogsForBooking(eq(model.getBookingId()), any()))
            .thenReturn(new PageImpl<>(List.of(model)));

        mockMvc.perform(get("/bookings/" + model.getBookingId() + "/share")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.shareBookingDTOList[0].id").value(model.getId().toString()))
            .andExpect(jsonPath("$._embedded.shareBookingDTOList[0].booking_id")
                           .value(model.getBookingId().toString()));
    }

    @DisplayName("Should return 404 error when booking not found")
    @Test
    void getShareLogsNotFound() throws Exception {
        var model = new CreateShareBookingDTO();
        model.setId(UUID.randomUUID());
        model.setBookingId(UUID.randomUUID());

        doThrow(new NotFoundException("Booking: " + model.getBookingId()))
            .when(shareBookingService)
            .getShareLogsForBooking(eq(model.getBookingId()), any());

        mockMvc.perform(get("/bookings/" + model.getBookingId() + "/share")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Booking: " + model.getBookingId()));

    }

    @DisplayName("Should undelete a booking by id and return a 200 response")
    @Test
    void undeleteBookingSuccess() throws Exception {
        var bookingId = UUID.randomUUID();
        doNothing().when(bookingService).undelete(bookingId);

        mockMvc.perform(post("/bookings/" + bookingId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should undelete a booking by id and return a 404 response")
    @Test
    void undeleteBookingNotFound() throws Exception {
        var bookingId = UUID.randomUUID();
        doThrow(
            new NotFoundException("Booking: " + bookingId)
        ).when(bookingService).undelete(bookingId);

        mockMvc.perform(post("/bookings/" + bookingId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Booking: " + bookingId));
    }

    private String getPath(UUID bookingId) {
        return "/bookings/" + bookingId;
    }

    private Set<CreateParticipantDTO> getCreateParticipantDTOs() {
        var witness = HelperFactory.createParticipantDTO("John", "Smith", ParticipantType.WITNESS);
        var defendant = HelperFactory.createParticipantDTO("John", "Smith", ParticipantType.DEFENDANT);
        return Set.of(witness, defendant);
    }
}
