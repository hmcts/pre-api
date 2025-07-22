package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.B2CController;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.VerifyEmailRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(B2CController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings({"PMD.LinguisticNaming"})
class B2CControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private EmailServiceFactory emailServiceFactory;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final String TEST_URL = "http://localhost";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @DisplayName("Should send email verification email")
    @Test
    void sendEmailVerificationEmail() throws Exception {

        var email = "test@test.com";
        var accessDTO = mock(AccessDTO.class);
        var user = mock(BaseUserDTO.class);
        when(accessDTO.getUser()).thenReturn(user);
        when(user.getFirstName()).thenReturn("Foo");
        when(user.getLastName()).thenReturn("Bar");

        var emailService = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService("GovNotify")).thenReturn(emailService);

        when(userService.findByEmail(email)).thenReturn(accessDTO);
        when(emailService.emailVerification(email, "Foo", "Bar", "123456"))
            .thenReturn(null); // no errors

        var request = new VerifyEmailRequestDTO();
        request.setEmail(email);
        request.setVerificationCode("123456");

        mockMvc.perform(post(TEST_URL + "/b2c/email-verification")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(request))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();
    }

    @DisplayName("Should fail send email verification email unknown email service")
    @Test
    void sendEmailVerificationEmailNoSuchEmailService() throws Exception {

        var email = "test@test.com";
        var accessDTO = mock(AccessDTO.class);
        var user = mock(BaseUserDTO.class);
        when(accessDTO.getUser()).thenReturn(user);
        when(user.getFirstName()).thenReturn("Foo");
        when(user.getLastName()).thenReturn("Bar");

        var emailService = mock(GovNotify.class);
        when(emailServiceFactory.getEnabledEmailService("GovNotify"))
            .thenThrow(new IllegalArgumentException("Unknown email service: GovNotify"));

        when(userService.findByEmail(email)).thenReturn(accessDTO);
        when(emailService.emailVerification(email, "Foo", "Bar", "123456"))
            .thenReturn(null); // no errors

        var request = new VerifyEmailRequestDTO();
        request.setEmail(email);
        request.setVerificationCode("123456");

        var response = mockMvc.perform(post(TEST_URL + "/b2c/email-verification")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(request))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is5xxServerError())
            .andReturn();
        assertThat(response.getResponse().getContentAsString())
            .contains("Unknown email service: GovNotify");
    }
}
