package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.TermsAndConditionsController;
import uk.gov.hmcts.reform.preapi.dto.TermsAndConditionsDTO;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.TermsAndConditionsService;
import uk.gov.hmcts.reform.preapi.services.UserTermsAcceptedService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TermsAndConditionsController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TermsAndConditionsControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private TermsAndConditionsService termsAndConditionsService;

    @MockitoBean
    private UserTermsAcceptedService userTermsAcceptedService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    private static final String TEST_URL = "http://localhost";

    @Test
    @DisplayName("Should get latest terms and conditions for app")
    void getLatestTermsAndConditionsAppSuccess() throws Exception {
        var model = new TermsAndConditionsDTO();
        model.setId(UUID.randomUUID());
        model.setHtml("some content");
        model.setType(TermsAndConditionsType.APP);
        model.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.APP))
            .thenReturn(model);

        mockMvc.perform(get(TEST_URL + "/app-terms-and-conditions/latest"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(model.getId().toString()))
            .andExpect(jsonPath("$.type").value(model.getType().toString()))
            .andExpect(jsonPath("$.html").value(model.getHtml()));

        verify(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.APP);
    }

    @Test
    @DisplayName("Should return 404 error when there are no terms and conditions for app")
    void getLatestTermsAndConditionsAppNotFound() throws Exception {
        doThrow(new NotFoundException("Terms and conditions of type: APP"))
            .when(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.APP);

        mockMvc.perform(get(TEST_URL + "/app-terms-and-conditions/latest"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Not found: Terms and conditions of type: APP"));

        verify(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.APP);
    }

    @Test
    @DisplayName("Should get latest terms and conditions for portal")
    void getLatestTermsAndConditionsPortalSuccess() throws Exception {
        var model = new TermsAndConditionsDTO();
        model.setId(UUID.randomUUID());
        model.setHtml("some content");
        model.setType(TermsAndConditionsType.PORTAL);
        model.setCreatedAt(Timestamp.from(Instant.now()));

        when(termsAndConditionsService.getLatestTermsAndConditions(TermsAndConditionsType.PORTAL))
            .thenReturn(model);

        mockMvc.perform(get(TEST_URL + "/portal-terms-and-conditions/latest"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(model.getId().toString()))
            .andExpect(jsonPath("$.type").value(model.getType().toString()))
            .andExpect(jsonPath("$.html").value(model.getHtml()));

        verify(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);
    }

    @Test
    @DisplayName("Should return 404 error when there are no terms and conditions for portal")
    void getLatestTermsAndConditionsPortalNotFound() throws Exception {
        doThrow(new NotFoundException("Terms and conditions of type: PORTAL"))
            .when(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);

        mockMvc.perform(get(TEST_URL + "/portal-terms-and-conditions/latest"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Not found: Terms and conditions of type: PORTAL"));

        verify(termsAndConditionsService).getLatestTermsAndConditions(TermsAndConditionsType.PORTAL);
    }

    @Test
    @DisplayName("Should return 204 when successfully accepting the terms and conditions")
    void acceptTermsAndConditionsSuccess() throws Exception {
        var id = UUID.randomUUID();

        mockMvc.perform(post(TEST_URL + "/accept-terms-and-conditions/" + id))
            .andExpect(status().isNoContent());

        verify(userTermsAcceptedService, times(1)).acceptTermsAndConditions(id);
    }

    @Test
    @DisplayName("Should return 404 when accepting terms and conditions encounters not found error")
    void acceptTermsAndConditionsNotFound() throws Exception {
        var id = UUID.randomUUID();

        doThrow(new NotFoundException("TermsAndConditions: " + id))
            .when(userTermsAcceptedService).acceptTermsAndConditions(id);

        mockMvc.perform(post(TEST_URL + "/accept-terms-and-conditions/" + id))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: TermsAndConditions: " + id));

        verify(userTermsAcceptedService, times(1)).acceptTermsAndConditions(id);
    }
}
