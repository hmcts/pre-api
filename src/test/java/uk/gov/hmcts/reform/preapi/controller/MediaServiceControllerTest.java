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
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.AzureMediaService;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaServiceController.class)
@AutoConfigureMockMvc(addFilters = false)
public class MediaServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaServiceBroker mediaServiceBroker;

    @MockBean
    private AzureMediaService mediaService;

    @MockBean
    private CaptureSessionService captureSessionService;

    @MockBean
    private MediaKind mediaKind;

    @MockBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private ScheduledTaskRunner taskRunner;

    @DisplayName("Should return 200 when successfully connected to media service")
    @Test
    void getMediaSuccess() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getAssets()).thenReturn(List.of());

        var response = mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString())
            .isEqualTo("successfully connected to media service (AzureMediaService)");
    }

    // todo remove this test with switch to mk
    @DisplayName("Should return 500 when cannot connect to media service")
    @Test
    void getMediaCannotConnect() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        // credentials error
        doThrow(new MsalServiceException("error", "something went wrong"))
            .when(mediaService).getAssets();

        var result = mockMvc.perform(get("/media-service/health"))
                             .andExpect(status().isInternalServerError())
                             .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                             .andReturn();
        assertThat(result
                       .getResponse()
                       .getContentAsString()
        ).contains("An error occurred when trying to communicate with Azure Media Service.");

        // resource manager issue
        doThrow(new ManagementException("error", mock(HttpResponse.class)))
            .when(mediaService).getAssets();

        var result2 = mockMvc.perform(get("/media-service/health"))
                             .andExpect(status().isInternalServerError())
                             .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                             .andReturn();
        assertThat(result2
                       .getResponse()
                       .getContentAsString()
        ).contains("An error occurred when trying to communicate with Azure Media Service.");
    }

    @DisplayName("Should return 200 and an asset")
    @Test
    void getAssetSuccess() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var name = UUID.randomUUID().toString();
        var asset = HelperFactory.createAsset(name, "description", "container", "storage account name");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
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
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
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
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
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
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
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
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
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
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvents()).thenReturn(liveEvents);

        mockMvc.perform(get("/media-service/live-events"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].name").value("name"))
            .andExpect(jsonPath("$[0].description").value("description"))
            .andExpect(jsonPath("$[0].resource_state").value("Stopped"))
            .andExpect(jsonPath("$[0].input_rtmp").value("rtmps://example.com"));
    }

    @DisplayName("Should return 200 and a CaptureSessionDTO with populated live_output_url and status as RECORDING")
    @Test
    void createStreamingLocatorSuccess() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.STANDBY);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(mediaService.playLiveEvent(captureSessionId)).thenReturn("https://www.gov.uk");

        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
                              .andExpect(status().isOk())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("\"live_output_url\":\"https://www.gov.uk\"");
        assertThat(response.getContentAsString()).contains("\"status\":\"RECORDING\"");
    }

    @DisplayName("Should return 200 with complete capture session withough calling mediakind")
    @Test
    void playLiveEventAlreadyRecordings() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.RECORDING);
        captureSession.setLiveOutputUrl("https://www.gov.uk");
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);

        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
                              .andExpect(status().isOk())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("\"live_output_url\":\"https://www.gov.uk\"");
        assertThat(response.getContentAsString()).contains("\"status\":\"RECORDING\"");

        verify(mediaService, never()).playLiveEvent(any());
    }

    @DisplayName("Should return 404 when capture session doesn't exist")
    @Test
    void captureSession404() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var captureSessionId = UUID.randomUUID();
        when(captureSessionService.findById(captureSessionId))
            .thenThrow(new NotFoundException("CaptureSession: " + captureSessionId));
        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
                              .andExpect(status().isNotFound())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("Not found: CaptureSession: " + captureSessionId);
    }

    @DisplayName("Should return 400 when capture session is not in a state of STANDBY")
    @Test
    void captureSession400() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.INITIALISING);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);

        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
                              .andExpect(status().isBadRequest())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString())
            .contains("Resource CaptureSessionDTO("
                          + captureSessionId
                          + ") is in a INITIALISING state. Expected state is STANDBY.");
    }

    @DisplayName("Should not create any resources when capture session already has a live_output_url set")
    @Test
    void captureSessionAlreadyHasLiveOutputUrl200() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.RECORDING);
        captureSession.setLiveOutputUrl("https://www.gov.uk");
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);

        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
                              .andExpect(status().isOk())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("\"live_output_url\":\"https://www.gov.uk\"");
        assertThat(response.getContentAsString()).contains("\"status\":\"RECORDING\"");

        verify(mediaService, times(0)).playLiveEvent(any());
    }

    @DisplayName("Should return 200 with capture session once live event is started")
    @Test
    void startLiveEventSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto1 = new CaptureSessionDTO();
        dto1.setId(captureSessionId);
        var dto2 = new CaptureSessionDTO();
        dto2.setId(captureSessionId);
        dto2.setStartedAt(Timestamp.from(Instant.now()));
        dto2.setStartedByUserId(UUID.randomUUID());
        dto2.setStatus(RecordingStatus.STANDBY);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto1);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.startLiveEvent(any())).thenReturn("example ingest");
        when(captureSessionService.startCaptureSession(captureSessionId, "example ingest")).thenReturn(dto2);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("STANDBY"))
            .andExpect(jsonPath("$.started_at").isNotEmpty());

        verify(mediaService, times(1)).startLiveEvent(dto1);
    }

    @DisplayName("Should return not found error when capture session does not exist")
    @Test
    void startLiveEventCaptureSessionNotFound() throws Exception {
        var captureSessionId = UUID.randomUUID();

        doThrow(new NotFoundException("Capture Session: " + captureSessionId))
            .when(captureSessionService).findById(captureSessionId);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Not found: Capture Session: " + captureSessionId));
    }

    @DisplayName("Should return conflict error when capture session has already finished")
    @Test
    void startLiveEventConflictAlreadyFinished() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setFinishedAt(Timestamp.from(Instant.now()));

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Conflict: Capture Session: " + captureSessionId + " has already been finished"));
    }

    @DisplayName("Should return capture session but do nothing when capture session already started")
    @Test
    void startLiveEventAlreadyStarted() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setStatus(RecordingStatus.STANDBY);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("STANDBY"))
            .andExpect(jsonPath("$.started_at").isNotEmpty());

        verify(mediaService, never()).startLiveEvent(any());
    }

    @DisplayName("Should update capture session and throw error when media service encounters an error")
    @Test
    void startLiveEventThrowError() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        doThrow(new NotFoundException("live event error"))
            .when(mediaService).startLiveEvent(dto);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
               .andExpect(status().isNotFound())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.message")
                              .value("Not found: live event error"));

        verify(captureSessionService, times(1)).startCaptureSession(captureSessionId, null);
    }

    @DisplayName("Should successfully stop capture session and return 200")
    @Test
    void stopCaptureSessionSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        var dto2 = new CaptureSessionDTO();
        dto2.setId(captureSessionId);
        dto2.setStatus(RecordingStatus.PROCESSING);
        var dto3 = new CaptureSessionDTO();
        dto3.setId(captureSessionId);
        dto3.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any()))
            .thenReturn(dto2);
        when(mediaService.stopLiveEvent(any(), any())).thenReturn(RecordingStatus.RECORDING_AVAILABLE);
        when(captureSessionService.stopCaptureSession(
            eq(captureSessionId),
            eq(RecordingStatus.RECORDING_AVAILABLE),
            any()
        )).thenReturn(dto3);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("RECORDING_AVAILABLE"));
    }

    @DisplayName("Should return 404 when capture session does not exist")
    @Test
    void stopCaptureSessionNotFound() throws Exception {
        var captureSessionId = UUID.randomUUID();

        doThrow(new NotFoundException("Capture Session: " + captureSessionId))
            .when(captureSessionService).findById(captureSessionId);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Not found: Capture Session: " + captureSessionId));
    }

    @DisplayName("Should return 200 when live event has already been finished")
    @Test
    void stopCaptureSessionAlreadyFinishedSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setFinishedAt(Timestamp.from(Instant.now()));

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("RECORDING_AVAILABLE"));
    }

    @DisplayName("Should throw 400 when live event has not been started")
    @Test
    void stopCaptureSessionNotStarted() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Resource: Capture Session(" + captureSessionId + ") has not been started."));
    }

    @DisplayName("Should throw 400 error when capture session in wrong status")
    @Test
    void stopCaptureSessionAlreadyFailed() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.FAILURE);
        dto.setStartedAt(Timestamp.from(Instant.now()));

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Resource Capture Session("
                                      + captureSessionId
                                      + ") is in a FAILURE state. Expected state is STANDBY or RECORDING."));
    }

    @DisplayName("Should update capture session when media service encounters error and return the error")
    @Test
    void stopCaptureSessionMediaServiceError() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        var dto2 = new CaptureSessionDTO();
        dto2.setId(captureSessionId);
        dto2.setStatus(RecordingStatus.PROCESSING);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any()))
            .thenReturn(dto2);
        doThrow(new NotFoundException("example error"))
            .when(mediaService).stopLiveEvent(any(), any());

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        verify(captureSessionService, times(2)).stopCaptureSession(any(), any(), any());
    }

    @DisplayName("Should return 200 and a CaptureSessionDTO with populated live_output_url and status as RECORDING")
    @Test
    void startLiveEventCaptureSessionBadState() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.FAILURE);

        when(captureSessionService.findById(captureSessionId))
            .thenReturn(dto);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
               .andExpect(status().is4xxClientError())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.message")
                              .value("Resource Capture Session("
                                     + captureSessionId
                                     + ") is in a FAILURE state. Expected state is INITIALISING."));
    }
}
