package uk.gov.hmcts.reform.preapi.controller;

import com.azure.core.http.HttpResponse;
import com.azure.core.management.exception.ManagementException;
import com.microsoft.aad.msal4j.MsalServiceException;
import feign.FeignException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.MediaServiceController;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.List;
import java.util.UUID;

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
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        // resource manager issue
        doThrow(new ManagementException("error", mock(HttpResponse.class)))
            .when(mediaService).getAssets();

        mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
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
        doThrow(FeignException.class).when(mediaKind).getAssets();

        mockMvc.perform(get("/media-service/health-mk"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Unable to connect to Media Service"));
    }

    @DisplayName("Should return 200 and an asset")
    @Test
    void getAssetSuccess() throws Exception {
        var name = UUID.randomUUID().toString();
        var asset = HelperFactory.createAsset(name, "description", "container", "storage account name");
        when(mediaService.getAsset(name)).thenReturn(asset);

        mockMvc.perform(get("/media-service/assets/" + name))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.description").value("description"))
            .andExpect(jsonPath("$.container").value("container"))
            .andExpect(jsonPath("$.storage_account_name").value("storage account name"));
    }

    @DisplayName("Should return 404 when asset cannot be found")
    @Test
    void getAssetNotFound() throws Exception {
        var name = UUID.randomUUID().toString();
        doThrow((new NotFoundException("Asset: " + name))).when(mediaKind).getAsset(name);

        mockMvc.perform(get("/media-service/assets/" + name))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: Asset: " + name));
    }

    @DisplayName("Should return 200 and a list of assets")
    @Test
    void getAssetsSuccess() throws Exception {
        var assets = List.of(
            HelperFactory.createAsset("name", "description", "container", "storage account")
        );
        when(mediaService.getAssets()).thenReturn(assets);

        mockMvc.perform(get("/media-service/assets"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("name"))
            .andExpect(jsonPath("$[0].description").value("description"))
            .andExpect(jsonPath("$[0].container").value("container"))
            .andExpect(jsonPath("$[0].storage_account_name").value("storage account"));
    }

    @DisplayName("Should return 200 and a live event")
    @Test
    void getLiveEventSuccess() throws Exception {
        var name = UUID.randomUUID().toString();
        var liveEvent = HelperFactory.createLiveEvent(name, "description", "Stopped", "rtmps://example.com");
        when(mediaService.getLiveEvent(name)).thenReturn(liveEvent);

        mockMvc.perform(get("/media-service/live-events/" + name))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.description").value("description"))
            .andExpect(jsonPath("$.resource_state").value("Stopped"))
            .andExpect(jsonPath("$.input_rtmp").value("rtmps://example.com"));
    }

    @DisplayName("Should return 404 when live event not found")
    @Test
    void getLiveEventNotFound() throws Exception {
        var name = UUID.randomUUID().toString();
        when(mediaService.getLiveEvent(name)).thenReturn(null);

        mockMvc.perform(get("/media-service/live-events/" + name))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: Live event: " + name));
    }

    @DisplayName("Should return 200 and a list of live events")
    @Test
    void getLiveEventsSuccess() throws Exception {
        var liveEvents = List.of(
            HelperFactory.createLiveEvent("name", "description", "Stopped", "rtmps://example.com")
        );
        when(mediaService.getLiveEvents()).thenReturn(liveEvents);

        mockMvc.perform(get("/media-service/live-events"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("name"))
            .andExpect(jsonPath("$[0].description").value("description"))
            .andExpect(jsonPath("$[0].resource_state").value("Stopped"))
            .andExpect(jsonPath("$[0].input_rtmp").value("rtmps://example.com"));
    }
}
