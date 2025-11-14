package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.AuditController;
import uk.gov.hmcts.reform.preapi.dto.AuditDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.AuditService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.preapi.config.OpenAPIConfiguration.X_USER_ID_HEADER;

@WebMvcTest(AuditController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"PMD.LinguisticNaming"})
class AuditControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private AuditService auditService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String getPath(UUID auditId) {
        return "/audit/" + auditId;
    }

    @BeforeAll
    public static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

    @Test
    @DisplayName("Should create an audit record with 201 response code")
    public void createAuditEndpointCreated() throws Exception {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();
        when(auditService.upsert(audit, xUserId)).thenReturn(UpsertResult.CREATED);

        var response = mockMvc.perform(put(getPath(audit.getId()))
                                                 .with(csrf())
                                                 .header(X_USER_ID_HEADER, xUserId)
                                                 .content(OBJECT_MAPPER.writeValueAsString(audit))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
    }

    @Test
    @DisplayName("Should create an audit record with 201 response code without x-user-id header")
    public void createAuditEndpointWithoutXUserIdCreated() throws Exception {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();
        when(auditService.upsert(audit, xUserId)).thenReturn(UpsertResult.CREATED);

        var response = mockMvc.perform(put(getPath(audit.getId()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(audit))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
    }

    @Test
    @DisplayName("Should fail to update an audit record as they are immutable")
    public void updateAuditFailure() throws Exception {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();
        when(auditService.upsert(any(), any())).thenThrow(new ImmutableDataException(audit.getId().toString()));

        mockMvc.perform(put(getPath(audit.getId()))
                            .with(csrf())
                            .header(X_USER_ID_HEADER, xUserId)
                            .content(OBJECT_MAPPER.writeValueAsString(audit))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Data is immutable and cannot be changed. Id: " + audit.getId()));
    }

    @Test
    @DisplayName("Should fail to create an audit record with 400 response code auditId mismatch")
    public void createAuditEndpointAuditIdMismatch() throws Exception {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();

        var response = mockMvc.perform(put(getPath(UUID.randomUUID()))
                                                 .with(csrf())
                                                 .header(X_USER_ID_HEADER, xUserId)
                                                 .content(OBJECT_MAPPER.writeValueAsString(audit))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path id does not match payload property createAuditDTO.id\"}");
    }

    @Test
    @DisplayName("Should fail to create an audit record with 400 response code")
    public void createAuditEndpointNotAcceptable() throws Exception {
        mockMvc.perform(put(getPath(UUID.randomUUID())))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Should get a list of audit logs with 200 response code")
    public void testSearchAuditLogsSuccess() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAuditDTO = new AuditDTO();
        mockAuditDTO.setId(auditLogId);
        var auditDTOList = List.of(mockAuditDTO);
        when(auditService.findAll(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit")
                            .with(csrf())
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1)).findAll(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Search audits by created after")
    public void searchLogsByCreatedAfter() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var searchTimestampAfterStr = "2021-01-01T00:00:00";
        when(auditService.findAll(any(Timestamp.class), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?after=" + searchTimestampAfterStr)
                                              .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        var argumentCaptor = ArgumentCaptor.forClass(Timestamp.class);

        verify(auditService, times(1))
            .findAll(argumentCaptor.capture(), any(), any(), any(), any(), any(), any(), any());

        assertThat(argumentCaptor.getValue().toString()).isEqualTo("2021-01-01 00:00:00.0");
    }

    @Test
    @DisplayName("Search audits by created before")
    public void searchLogsByCreatedBefore() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var searchTimestampBeforeStr = "2021-01-01T00:00:00";
        when(auditService.findAll(any(), any(Timestamp.class), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?before=" + searchTimestampBeforeStr)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        var argumentCaptor = ArgumentCaptor.forClass(Timestamp.class);

        verify(auditService, times(1))
            .findAll(any(), argumentCaptor.capture(), any(), any(), any(), any(), any(), any());

        assertThat(argumentCaptor.getValue().toString()).isEqualTo("2021-01-01 00:00:00.0");
    }

    @Test
    @DisplayName("Search audits by functional area")
    public void searchLogsByFunctionalArea() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var functionalArea = "API";
        when(auditService.findAll(any(), any(), eq(functionalArea), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?functionalArea=" + functionalArea)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1))
            .findAll(any(), any(), eq(functionalArea), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Search audits by source")
    public void searchLogsBySource() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var source = AuditLogSource.AUTO;
        when(auditService.findAll(any(), any(), any(), eq(source), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?source=" + source)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1))
            .findAll(any(), any(), any(), eq(source), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Search audits by user's name")
    public void searchLogsByUserName() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var name = "someone";
        when(auditService.findAll(any(), any(), any(), any(), eq(name), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?userName=" + name)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1))
            .findAll(any(), any(), any(), any(), eq(name), any(), any(), any());
    }

    @Test
    @DisplayName("Search audits by court id")
    public void searchLogsByCourtId() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var courtId = UUID.randomUUID();
        when(auditService.findAll(any(), any(), any(), any(), any(), eq(courtId), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?courtId=" + courtId)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1))
            .findAll(any(), any(), any(), any(), any(), eq(courtId), any(), any());
    }

    @Test
    @DisplayName("Search audits by case reference")
    public void searchLogsByCaseReference() throws Exception {
        var auditLogId = UUID.randomUUID();
        var mockAudit = new AuditDTO();
        mockAudit.setId(auditLogId);
        var auditDTOList = List.of(mockAudit);
        var caseReference = "CASE123";
        when(auditService.findAll(any(), any(), any(), any(), any(), any(), eq(caseReference), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?caseReference=" + caseReference)
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.auditDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.auditDTOList[0].id").value(auditLogId.toString()));

        verify(auditService, times(1))
            .findAll(any(), any(), any(), any(), any(), any(), eq(caseReference), any());
    }

    @Test
    @DisplayName("Requested page out of range")
    public void getAuditLogsRequestedPageOutOfRange() throws Exception {
        UUID auditLogId = UUID.randomUUID();
        var mockAuditDTO = new AuditDTO();
        mockAuditDTO.setId(auditLogId);
        var auditDTOList = List.of(mockAuditDTO);
        when(auditService.findAll(any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(new PageImpl<>(auditDTOList));

        mockMvc.perform(get("/audit?page=5"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Requested page {5} is out of range. Max page is {1}"));
    }
}
