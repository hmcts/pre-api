package uk.gov.hmcts.reform.preapi.controller;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.microsoft.aad.msal4j.MsalServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.MediaServiceController;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(MediaServiceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MediaServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AzureMediaService mediaService;

    @MockBean
    private MediaKind mediaKind;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @DisplayName("Should return 200 when successfully connected to media service")
    @Test
    void getMediaSuccess() throws Exception {
        when(mediaService.getAssets()).thenReturn(List.of());

        var response = mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEqualTo("successfully connected to media service (ams)");
    }

    // todo remove this test with switch to mk
    @DisplayName("Should return 500 when cannot connect to media service")
    @Test
    void getMediaCannotConnect() throws Exception {
        // credentials error
        doThrow(new MsalServiceException("error", "something went wrong"))
            .when(mediaService).getAssets();

        mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("An error occurred when trying to communicate with Azure Media Service."));

        // resource manager issue
        doThrow(new ManagementException("error", mock(HttpResponse.class)))
            .when(mediaService).getAssets();

        mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("An error occurred when trying to communicate with Azure Media Service."));
    }

    // todo remove this test with switch to mk
    @DisplayName("Should return 200 when successfully connected to media service (mediakind)")
    @Test
    void getMediaMkSuccess() throws Exception {
        when(mediaKind.getAssets()).thenReturn(List.of());

        var response = mockMvc.perform(get("/media-service/health-mk"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEqualTo("successfully connected to media service (mk)");
    }

    // todo update this test with switch to mk
    @DisplayName("Should return 500 when cannot connect to media service (mk)")
    @Test
    void getMediaMkCannotConnect() throws Exception {
        doThrow(new MediaKindException()).when(mediaKind).getAssets();

        mockMvc.perform(get("/media-service/health-mk"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Unable to connect to Media Service"));
    }
}
