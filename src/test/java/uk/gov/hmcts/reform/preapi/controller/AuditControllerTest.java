package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.AuditController;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.AuditService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.text.SimpleDateFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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

    @BeforeAll
    static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

    @DisplayName("Should create an audit record with 201 response code")
    @Test
    void createAuditEndpointCreated() throws Exception {

        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();
        when(auditService.upsert(audit, xUserId)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(audit.getId()))
                                                 .with(csrf())
                                                 .header(X_USER_ID_HEADER, xUserId)
                                                 .content(OBJECT_MAPPER.writeValueAsString(audit))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
    }

    @DisplayName("Should create an audit record with 201 response code without x-user-id header")
    @Test
    void createAuditEndpointWithoutXUserIdCreated() throws Exception {

        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();
        when(auditService.upsert(audit, xUserId)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(audit.getId()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(audit))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
    }

    @DisplayName("Should fail to update an audit record as they are immutable")
    @Test
    void updateAuditFailure() throws Exception {

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

    @DisplayName("Should fail to create an audit record with 400 response code auditId mismatch")
    @Test
    void createAuditEndpointAuditIdMismatch() throws Exception {

        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var xUserId = UUID.randomUUID();

        MvcResult response = mockMvc.perform(put(getPath(UUID.randomUUID()))
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

    @DisplayName("Should fail to create an audit record with 400 response code")
    @Test
    void createAuditEndpointNotAcceptable() throws Exception {

        mockMvc.perform(put(getPath(UUID.randomUUID())))
            .andExpect(status().is4xxClientError());
    }

    private String getPath(UUID auditId) {
        return "/audit/" + auditId;
    }
}
