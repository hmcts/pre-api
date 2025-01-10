package uk.gov.hmcts.reform.preapi.controller;

import com.azure.resourcemanager.mediaservices.models.JobState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.MediaServiceController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MediaServiceController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("LineLength")
public class MediaServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MediaServiceBroker mediaServiceBroker;

    @MockBean
    private CaptureSessionService captureSessionService;

    @MockBean
    private RecordingService recordingService;

    @MockBean
    private MediaKind mediaService;

    @MockBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private ScheduledTaskRunner taskRunner;

    @MockBean
    private AzureFinalStorageService azureFinalStorageService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @DisplayName("Should return 200 when successfully connected to media service")
    @Test
    void getMediaSuccess() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getAssets()).thenReturn(List.of());

        var response = mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEqualTo("successfully connected to media service (MediaKind)");
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
        doThrow((new NotFoundException("Asset: " + name))).when(mediaService).getAsset(name);

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

    @DisplayName("Should update corresponding capture session if status is initialising")
    @Test
    void getLiveEventUpdateCaptureSessionStart() throws Exception {
        var id = UUID.randomUUID();
        var name = id.toString().replace("-", "");
        var dto = new CaptureSessionDTO();
        dto.setId(id);
        dto.setStatus(RecordingStatus.INITIALISING);
        var liveEvent = HelperFactory.createLiveEvent(name, "description", "Running", "rtmps://example.com");

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvent(name)).thenReturn(liveEvent);
        when(captureSessionService.findByLiveEventId(name)).thenReturn(dto);

        mockMvc.perform(get("/media-service/live-events/" + name))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.description").value("description"))
            .andExpect(jsonPath("$.resource_state").value("Running"))
            .andExpect(jsonPath("$.input_rtmp").value("rtmps://example.com"));

        verify(captureSessionService, times(1)).findByLiveEventId(name);
        verify(captureSessionService, times(1)).startCaptureSession(id, RecordingStatus.STANDBY, "rtmps://example.com");
    }

    @DisplayName("Should not error when cannot find corresponding capture sessions")
    @Test
    void getLiveEventUpdateCaptureSessionNotFoundStart() throws Exception {
        var id = UUID.randomUUID();
        var name = id.toString().replace("-", "");
        var liveEvent = HelperFactory.createLiveEvent(name, "description", "Running", "rtmps://example.com");

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvent(name)).thenReturn(liveEvent);
        doThrow(NotFoundException.class).when(captureSessionService).findByLiveEventId(name);

        mockMvc.perform(get("/media-service/live-events/" + name))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.name").value(name))
            .andExpect(jsonPath("$.description").value("description"))
            .andExpect(jsonPath("$.resource_state").value("Running"))
            .andExpect(jsonPath("$.input_rtmp").value("rtmps://example.com"));

        verify(captureSessionService, times(1)).findByLiveEventId(name);
        verify(captureSessionService, never()).startCaptureSession(id, RecordingStatus.STANDBY, "rtmps://example.com");
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
        captureSession.setBookingId(UUID.randomUUID());
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(mediaService.playLiveEvent(captureSessionId)).thenReturn("https://www.gov.uk");
        when(azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString()))
            .thenReturn(true);

        var response = mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
            .andExpect(status().isOk())
            .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains("\"live_output_url\":\"https://www.gov.uk\"");
        assertThat(response.getContentAsString()).contains("\"status\":\"RECORDING\"");
    }

    @DisplayName("Should return 200 with complete capture session without calling mediakind")
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

    @DisplayName("Should create endpoint and locator when capture session status = RECORDING but liveOutputUrl = null")
    @Test
    void playLiveEventRecordingButNoLiveOutputUrl() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.RECORDING);
        captureSession.setBookingId(UUID.randomUUID());

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString()))
            .thenReturn(true);
        when(mediaService.playLiveEvent(captureSessionId))
            .thenReturn("https://example.com");

        mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("RECORDING"))
            .andExpect(jsonPath("$.live_output_url").value("https://example.com"));
    }

    @DisplayName("Should return 200 and playback information")
    @Test
    void getVodSuccess() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new RecordingDTO();
        recording.setId(recordingId);
        var captureSession = new CaptureSessionDTO();
        captureSession.setCaseState(CaseState.OPEN);
        recording.setCaptureSession(captureSession);

        var user = mockAdminUser();
        var assetName = recordingId.toString().replace("-", "") + "_output";
        var playback = new PlaybackDTO("dash", "hls", "license", "token");

        when(recordingService.findById(recordingId)).thenReturn(recording);
        when(mediaServiceBroker.getEnabledMediaService(null)).thenReturn(mediaService);
        when(mediaService.playAsset(assetName, user.getUserId().toString())).thenReturn(playback);

        mockMvc.perform(get("/media-service/vod")
                            .param("recordingId", recordingId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dash_url").value("dash"))
            .andExpect(jsonPath("$.hls_url").value("hls"))
            .andExpect(jsonPath("$.license_url").value("license"))
            .andExpect(jsonPath("$.token").value("token"));
    }

    @DisplayName("Should return 404 when recording does not exist")
    @Test
    void getVodRecordingNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        doThrow(new NotFoundException("Recording: " + recordingId))
            .when(recordingService).findById(recordingId);

        mockMvc.perform(get("/media-service/vod")
                            .param("recordingId", recordingId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Recording: " + recordingId));
    }

    @DisplayName("Should return 400 when recording's case has been closed")
    @Test
    void getVodRecordingClosedBadRequest() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new RecordingDTO();
        recording.setId(recordingId);
        var captureSession = new CaptureSessionDTO();
        captureSession.setCaseState(CaseState.CLOSED);
        recording.setCaptureSession(captureSession);

        when(recordingService.findById(recordingId)).thenReturn(recording);

        mockMvc.perform(get("/media-service/vod")
                            .param("recordingId", recordingId.toString()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Case associated with Recording("
                                      + recordingId
                                      + ") is in state CLOSED. Cannot play recording."));
    }

    @DisplayName("Should return 200 with capture session once live event is started")
    @Test
    void startLiveEventSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto1 = new CaptureSessionDTO();
        dto1.setId(captureSessionId);
        dto1.setCaseState(CaseState.OPEN);
        var dto2 = new CaptureSessionDTO();
        dto2.setId(captureSessionId);
        dto2.setStartedAt(Timestamp.from(Instant.now()));
        dto2.setStartedByUserId(UUID.randomUUID());
        dto2.setStatus(RecordingStatus.INITIALISING);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto1);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.INITIALISING, null))
            .thenReturn(dto2);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("INITIALISING"))
            .andExpect(jsonPath("$.started_at").isNotEmpty());

        verify(mediaService, times(1)).startLiveEvent(dto1);
    }

    @DisplayName("Should return 400 when case associated with capture session is not open")
    @Test
    void startLiveEventCaseClosedBadRequest() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto1 = new CaptureSessionDTO();
        dto1.setId(captureSessionId);
        dto1.setCaseState(CaseState.CLOSED);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto1);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Resource Capture Session("
                                      + dto1.getId()
                                      + ") is associated with a case in the state CLOSED. Must be in state OPEN."));

        verify(mediaService, never()).startLiveEvent(dto1);
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
        dto.setCaseState(CaseState.OPEN);

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
        dto.setCaseState(CaseState.OPEN);

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
        dto.setCaseState(CaseState.OPEN);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        doThrow(new NotFoundException("live event error"))
            .when(mediaService).startLiveEvent(dto);

        mockMvc.perform(put("/media-service/live-event/start/" + captureSessionId))
               .andExpect(status().isNotFound())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.message")
                              .value("Not found: live event error"));

        verify(captureSessionService, times(1)).startCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
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

    @Test
    @DisplayName("Should throw 500 when encountering failure in encoding process")
    void stopLiveEventFailureEncounteredInternalServerError() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);
        dto.setStartedAt(Timestamp.from(Instant.now()));

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        when(captureSessionService.stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any(UUID.class)))
            .thenReturn(dto);
        when(mediaService.stopLiveEvent(any(CaptureSessionDTO.class), any(UUID.class))).thenReturn(RecordingStatus.FAILURE);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value(
                               "Unknown Server Exception: Encountered an error during encoding process for CaptureSession("
                                   + captureSessionId
                                   + ")"));

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(captureSessionService, times(1)).findById(captureSessionId);
        verify(captureSessionService, times(1)).stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any(UUID.class));
        verify(mediaService, times(1)).stopLiveEvent(any(CaptureSessionDTO.class), any(UUID.class));
        verify(captureSessionService, times(1)).stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.FAILURE), any(UUID.class));
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
        dto.setCaseState(CaseState.OPEN);

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

    @DisplayName("Should return 204 when ism file exists")
    @Test
    void checkBlobExistsSuccess() throws Exception {
        var containerName = "container";
        when(azureFinalStorageService.doesIsmFileExist(containerName)).thenReturn(true);

        var response = mockMvc.perform(get("/media-service/blob/" + containerName))
                              .andExpect(status().isNoContent())
                              .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEmpty();
    }

    @DisplayName("Should return 404 when ism file exists")
    @Test
    void checkBlobExistsFail() throws Exception {
        var containerName = "container";
        when(azureFinalStorageService.doesIsmFileExist(containerName)).thenReturn(false);

        mockMvc.perform(get("/media-service/blob/" + containerName))
               .andExpect(status().isNotFound());
    }

    @DisplayName("Should return 200 with capture session when status is already RECORDING")
    @Test
    void checkStreamCaptureSessionAlreadyStatusRecording() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.RECORDING);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dto.getId().toString()))
            .andExpect(jsonPath("$.status").value(RecordingStatus.RECORDING.toString()));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, never()).doesIsmFileExist(any());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @DisplayName("Should throw 400 when capture session already finished")
    @Test
    void checkStreamCaptureSessionAlreadyFinished() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setFinishedAt(Timestamp.from(Instant.now()));
        dto.setStatus(RecordingStatus.STANDBY);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Unprocessable Content: Resource: Capture Session("
                                      + dto.getId() + ") has already finished."));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, never()).doesIsmFileExist(any());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @DisplayName("Should throw 400 when capture session has not been started")
    @Test
    void checkStreamCaptureSessionNotStarted() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Unprocessable Content: Resource: Capture Session("
                                      + dto.getId() + ") has not been started."));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, never()).doesIsmFileExist(any());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @DisplayName("Should throw 400 when capture session has wrong status")
    @Test
    void checkStreamCaptureSessionInvalidStatus() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.FAILURE);
        dto.setStartedAt(Timestamp.from(Instant.now()));

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message")
                           .value("Unprocessable Content: Resource: Capture Session("
                                      + dto.getId()
                                      + ") is in a FAILURE state. Expected state is STANDBY."));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, never()).doesIsmFileExist(any());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @DisplayName("Should return 200 with updated capture session when .ism file exists")
    @Test
    void checkStreamCaptureSessionIsmFileExists() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setBookingId(UUID.randomUUID());

        var dto2 = new CaptureSessionDTO();
        dto2.setId(dto.getId());
        dto2.setStatus(RecordingStatus.RECORDING);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);
        when(azureIngestStorageService.doesIsmFileExist(dto.getBookingId().toString())).thenReturn(true);
        when(captureSessionService.setCaptureSessionStatus(dto.getId(), RecordingStatus.RECORDING)).thenReturn(dto2);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dto.getId().toString()))
            .andExpect(jsonPath("$.status").value(RecordingStatus.RECORDING.toString()));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, times(1)).doesIsmFileExist(dto.getBookingId().toString());
        verify(captureSessionService, times(1)).setCaptureSessionStatus(dto.getId(), RecordingStatus.RECORDING);
    }

    @DisplayName("Should return 200 with updated capture session when gc_state exists")
    @Test
    void checkStreamCaptureSessionGcStateExists() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setBookingId(UUID.randomUUID());

        var dto2 = new CaptureSessionDTO();
        dto2.setId(dto.getId());
        dto2.setStatus(RecordingStatus.RECORDING);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);
        when(azureIngestStorageService.doesIsmFileExist(dto.getBookingId().toString())).thenReturn(false);
        when(azureIngestStorageService.doesBlobExist(dto.getBookingId().toString(), "gc_state")).thenReturn(true);
        when(captureSessionService.setCaptureSessionStatus(dto.getId(), RecordingStatus.RECORDING)).thenReturn(dto2);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.id").value(dto.getId().toString()))
            .andExpect(jsonPath("$.status").value(RecordingStatus.RECORDING.toString()));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, times(1)).doesIsmFileExist(dto.getBookingId().toString());
        verify(captureSessionService, times(1)).setCaptureSessionStatus(dto.getId(), RecordingStatus.RECORDING);
    }

    @DisplayName("Should return 200 with updated capture session when .ism file does not exist")
    @Test
    void checkStreamCaptureSessionIsmFileNotExists() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setBookingId(UUID.randomUUID());

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);
        when(azureIngestStorageService.doesIsmFileExist(dto.getBookingId().toString())).thenReturn(false);
        when(azureIngestStorageService.doesBlobExist(dto.getBookingId().toString(), "gc_state")).thenReturn(false);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: No stream found"));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, times(1)).doesIsmFileExist(dto.getBookingId().toString());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @DisplayName("Should return 404 when .ism file/gc_state does not exist (recording has not started)")
    @Test
    void createLiveEventStreamingLocatorIsmNotFound() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.STANDBY);
        captureSession.setBookingId(UUID.randomUUID());

        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString()))
            .thenReturn(false);
        when(azureIngestStorageService.doesBlobExist(captureSession.getBookingId().toString(), "gc_state"))
            .thenReturn(false);

        mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Asset for capture session: " + captureSessionId + " found with no .ism file"));


        verify(azureIngestStorageService, times(1))
            .doesIsmFileExist(captureSession.getBookingId().toString());
        verify(azureIngestStorageService, times(1)).doesBlobExist(captureSession.getBookingId().toString(), "gc_state");
        verify(mediaService, never()).playLiveEvent(any());
    }

    @DisplayName("Should return a 404 when the source container doesn't exist")
    @Test
    void generateAsset404NoSourceContainer() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID() + "-input");
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenReturn(false);
        mockMvc.perform(post("/media-service/generate-asset")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(generateAssetDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("Not found: Source Container: " + generateAssetDTO.getSourceContainer()));
    }

    @DisplayName("Should return a 404 when the source blob doesn't exist")
    @Test
    @SuppressWarnings("LineLength")
    void generateAsset404NoSourceBlob() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID() + "-input");
        generateAssetDTO.setDestinationContainer(UUID.randomUUID());
        generateAssetDTO.setTempAsset("blobby");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenReturn(true);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenThrow(new NotFoundException("No files ending .mp4 were found in the Source Container " + generateAssetDTO.getSourceContainer()));
        mockMvc.perform(post("/media-service/generate-asset")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(generateAssetDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message").value("Not found: No files ending .mp4 were found in the Source Container " + generateAssetDTO.getSourceContainer()));
    }

    @DisplayName("Should return a 400 when incorrect body provided")
    @Test
    void generateAssetTest400Error() throws Exception {
        var response = mockMvc.perform(post("/media-service/generate-asset"))
                              .andExpect(status().is4xxClientError())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains(
            "Required request body is missing: public org.springframework.http.ResponseEntity"
                + "<uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO>");
    }

    @DisplayName("Should return a 400 when incorrect source container name provided")
    @Test
    void generateAssetTest400SourceContainerNAme() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID().toString());
        generateAssetDTO.setDestinationContainer(UUID.randomUUID());
        generateAssetDTO.setTempAsset("blobby");

        var response = mockMvc.perform(post("/media-service/generate-asset")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(generateAssetDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
                            .andExpect(status().is4xxClientError())
                            .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains(
            "{\"sourceContainer\":\"must match \\\"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-input$\\\"\"}"
        );
    }

    @DisplayName("Should return a GenerateAssetResponseDTO successfully")
    @Test
    @SuppressWarnings("unchecked")
    void generateAssetTest200() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID() + "-input");
        generateAssetDTO.setDestinationContainer(UUID.randomUUID());
        generateAssetDTO.setTempAsset("blobby");
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenReturn(true);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getDestinationContainer().toString())).thenReturn(true);
        when(azureFinalStorageService.getMp4FileName(generateAssetDTO.getSourceContainer())).thenReturn("blobby.mp4");
        when(azureFinalStorageService.getMp4FileName(generateAssetDTO.getDestinationContainer().toString())).thenReturn("something-else.mp4");

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.importAsset(any())).thenReturn(
            new GenerateAssetResponseDTO("asset", "container", "description", JobState.FINISHED.toString())
        );

        var parentRecording = new RecordingDTO();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(UUID.randomUUID());
        parentRecording.setCaptureSession(captureSession);

        when(recordingService.findById(generateAssetDTO.getParentRecordingId()))
            .thenReturn(parentRecording);

        when(recordingService.findAll(any(SearchRecordings.class), eq(true), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(parentRecording)));

        var response = mockMvc.perform(post("/media-service/generate-asset")
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(generateAssetDTO))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
                              .andExpect(status().isOk())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).isEqualTo(
            "{\"asset\":\"asset\","
            + "\"container\":\"container\","
            + "\"description\":\"description\","
            + "\"jobStatus\":\"Finished\"}");

        var recordingArgument = ArgumentCaptor.forClass(CreateRecordingDTO.class);

        verify(recordingService, times(1)).upsert(recordingArgument.capture());
        assertThat(recordingArgument.getValue().getVersion()).isEqualTo(2);
        assertThat(recordingArgument.getValue().getFilename()).isEqualTo("something-else.mp4");
    }

    protected static UserAuthentication mockAdminUser() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.getUserId()).thenReturn(UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
        return mockAuth;
    }
}
