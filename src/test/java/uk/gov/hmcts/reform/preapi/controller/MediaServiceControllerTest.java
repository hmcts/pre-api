package uk.gov.hmcts.reform.preapi.controller;

import com.azure.resourcemanager.mediaservices.models.JobState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.MediaKind;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.EncodeJobService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
@TestPropertySource(properties = {"feature-flags.enable-enhanced-processing=true"})
public class MediaServiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MediaServiceBroker mediaServiceBroker;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private MediaKind mediaService;

    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private EncodeJobService encodeJobService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    @DisplayName("Should return 200 when successfully connected to media service")
    void getMediaSuccess() throws Exception {
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getAssets()).thenReturn(List.of());

        var response = mockMvc.perform(get("/media-service/health"))
            .andExpect(status().isOk())
            .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEqualTo("successfully connected to media service (MediaKind)");
    }

    @Test
    @DisplayName("Should return 200 and an asset")
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

    @Test
    @DisplayName("Should return 404 when asset cannot be found")
    void getAssetNotFound() throws Exception {
        var name = UUID.randomUUID().toString();
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        doThrow((new NotFoundException("Asset: " + name))).when(mediaService).getAsset(name);

        mockMvc.perform(get("/media-service/assets/" + name))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: Asset: " + name));
    }

    @Test
    @DisplayName("Should return 200 and a list of assets")
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

    @Test
    @DisplayName("Should return 200 and a live event")
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

    @Test
    @DisplayName("Should return 404 when live event not found")
    void getLiveEventNotFound() throws Exception {
        var name = UUID.randomUUID().toString();
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.getLiveEvent(name)).thenReturn(null);

        mockMvc.perform(get("/media-service/live-events/" + name))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: Live event: " + name));
    }

    @Test
    @DisplayName("Should update corresponding capture session if status is initialising")
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

    @Test
    @DisplayName("Should not error when cannot find corresponding capture sessions")
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

    @Test
    @DisplayName("Should return 200 and a list of live events")
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

    @Test
    @DisplayName("Should return 200 and a CaptureSessionDTO with populated live_output_url and status as RECORDING")
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

    @Test
    @DisplayName("Should return 200 with complete capture session without calling mediakind")
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

    @Test
    @DisplayName("Should return 404 when capture session doesn't exist")
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

    @Test
    @DisplayName("Should return 400 when capture session is not in a state of STANDBY")
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

    @Test
    @DisplayName("Should not create any resources when capture session already has a live_output_url set")
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

    @Test
    @DisplayName("Should create endpoint and locator when capture session status = RECORDING but liveOutputUrl = null")
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

    @Test
    @DisplayName("Should return 200 and playback information")
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

    @Test
    @DisplayName("Should return 404 when recording does not exist")
    void getVodRecordingNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        doThrow(new NotFoundException("Recording: " + recordingId))
            .when(recordingService).findById(recordingId);

        mockMvc.perform(get("/media-service/vod")
                            .param("recordingId", recordingId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Recording: " + recordingId));
    }

    @Test
    @DisplayName("Should return 400 when recording's case has been closed")
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

    @Test
    @DisplayName("Should return 200 with capture session once live event is started")
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

    @Test
    @DisplayName("Should return 400 when case associated with capture session is not open")
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

    @Test
    @DisplayName("Should return not found error when capture session does not exist")
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

    @Test
    @DisplayName("Should return conflict error when capture session has already finished")
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

    @Test
    @DisplayName("Should return capture session but do nothing when capture session already started")
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

    @Test
    @DisplayName("Should update capture session and throw error when media service encounters an error")
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

    @Test
    @DisplayName("Should successfully stop capture session and return 200")
    void stopCaptureSessionSuccess() throws Exception {
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
        when(mediaService.triggerProcessingStep1(eq(dto), eq(dto.getId().toString().replace("-", "")), any(UUID.class)))
            .thenReturn("jobName");
        when(captureSessionService.stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any()))
            .thenReturn(dto2);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
               .andExpect(status().isOk())
               .andExpect(content().contentType(MediaType.APPLICATION_JSON))
               .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
               .andExpect(jsonPath("$.status").value("PROCESSING"));

        verify(captureSessionService, times(1)).findById(captureSessionId);
        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).stopLiveEvent(eq(dto), any(UUID.class));
        verify(mediaService, times(1)).triggerProcessingStep1(eq(dto), anyString(), any(UUID.class));
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.PROCESSING), any(UUID.class));
        verify(captureSessionService, never())
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.NO_RECORDING), isNull());
        verify(captureSessionService, never())
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.FAILURE), isNull());
    }

    @DisplayName("Should successfully stop capture session and return 200 when no recording found")
    @Test
    void stopCaptureSessionNoRecordingSuccess() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        var dto2 = new CaptureSessionDTO();
        dto2.setId(captureSessionId);
        dto2.setStatus(RecordingStatus.NO_RECORDING);

        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.triggerProcessingStep1(eq(dto), eq(dto.getId().toString().replace("-", "")), any(UUID.class)))
            .thenReturn(null);
        when(captureSessionService.stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.NO_RECORDING), any()))
            .thenReturn(dto2);

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(captureSessionId.toString()))
            .andExpect(jsonPath("$.status").value("NO_RECORDING"));

        verify(captureSessionService, times(1)).findById(captureSessionId);
        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(mediaService, times(1)).stopLiveEvent(eq(dto), any(UUID.class));
        verify(mediaService, times(1)).triggerProcessingStep1(eq(dto), anyString(), any(UUID.class));
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.NO_RECORDING), isNull());
    }

    @Test
    @DisplayName("Should return 404 when capture session does not exist")
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

    @Test
    @DisplayName("Should return 200 when live event has already been finished")
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
    @DisplayName("Should update capture session when media service encounters error and return the error")
    void stopLiveEventFailureEncounteredInternalServerError() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var dto = new CaptureSessionDTO();
        dto.setId(captureSessionId);
        dto.setStatus(RecordingStatus.RECORDING);
        dto.setStartedAt(Timestamp.from(Instant.now()));

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(captureSessionService.findById(captureSessionId)).thenReturn(dto);
        doThrow(new UnknownServerException("Something went wrong"))
            .when(mediaService).triggerProcessingStep1(any(), any(), any());

        mockMvc.perform(put("/media-service/live-event/end/" + captureSessionId))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Unknown Server Exception: Something went wrong"));

        verify(mediaServiceBroker, times(1)).getEnabledMediaService();
        verify(captureSessionService, times(1)).findById(captureSessionId);
        verify(mediaService, times(1)).stopLiveEvent(any(CaptureSessionDTO.class), any(UUID.class));
        verify(mediaService, times(1)).triggerProcessingStep1(any(), any(), any());
        verify(captureSessionService, times(1))
            .stopCaptureSession(eq(captureSessionId), eq(RecordingStatus.FAILURE), isNull());
    }

    @Test
    @DisplayName("Should throw 400 when live event has not been started")
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

    @Test
    @DisplayName("Should throw 400 error when capture session in wrong status")
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

    @Test
    @DisplayName("Should return 200 and a CaptureSessionDTO with populated live_output_url and status as RECORDING")
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

    @Test
    @DisplayName("Should return 204 when ism file exists")
    void checkBlobExistsSuccess() throws Exception {
        var containerName = "container";
        when(azureFinalStorageService.doesIsmFileExist(containerName)).thenReturn(true);

        var response = mockMvc.perform(get("/media-service/blob/" + containerName))
                              .andExpect(status().isNoContent())
                              .andReturn().getResponse();

        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    @DisplayName("Should return 404 when ism file exists")
    void checkBlobExistsFail() throws Exception {
        var containerName = "container";
        when(azureFinalStorageService.doesIsmFileExist(containerName)).thenReturn(false);

        mockMvc.perform(get("/media-service/blob/" + containerName))
               .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Should return 200 with capture session when status is already RECORDING")
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

    @Test
    @DisplayName("Should return 200 when capture session already finished")
    void checkStreamCaptureSessionAlreadyFinished() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setFinishedAt(Timestamp.from(Instant.now()));
        dto.setStatus(RecordingStatus.STANDBY);

        when(captureSessionService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(dto.getId().toString()));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, never()).doesIsmFileExist(any());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @Test
    @DisplayName("Should throw 400 when capture session has not been started")
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

    @Test
    @DisplayName("Should throw 400 when capture session has wrong status")
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

    @Test
    @DisplayName("Should return 200 with updated capture session when .ism file exists")
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

    @Test
    @DisplayName("Should return 200 with updated capture session when streaming path exists")
    void checkStreamCaptureSessionGcStateExists() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setBookingId(UUID.randomUUID());

        var dto2 = new CaptureSessionDTO();
        dto2.setId(dto.getId());
        dto2.setStatus(RecordingStatus.RECORDING);

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.checkLiveFeedAvailable(dto.getId())).thenReturn(true);
        when(captureSessionService.findById(dto.getId())).thenReturn(dto);
        when(azureIngestStorageService.doesIsmFileExist(dto.getBookingId().toString())).thenReturn(false);
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

    @Test
    @DisplayName("Should return 404 with updated capture session when .ism file does not exist")
    void checkStreamCaptureSessionIsmFileNotExists() throws Exception {
        var dto = new CaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setStartedAt(Timestamp.from(Instant.now()));
        dto.setBookingId(UUID.randomUUID());

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.checkLiveFeedAvailable(dto.getId())).thenReturn(false);
        when(captureSessionService.findById(dto.getId())).thenReturn(dto);
        when(azureIngestStorageService.doesIsmFileExist(dto.getBookingId().toString())).thenReturn(false);

        mockMvc.perform(post("/media-service/live-event/check/" + dto.getId()))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.message").value("Not found: No stream found"));

        verify(captureSessionService, times(1)).findById(dto.getId());
        verify(azureIngestStorageService, times(1)).doesIsmFileExist(dto.getBookingId().toString());
        verify(captureSessionService, never()).setCaptureSessionStatus(any(), any());
    }

    @Test
    @DisplayName("Should return 404 when .ism file does not exist (recording has not started)")
    void createLiveEventStreamingLocatorIsmNotFound() throws Exception {
        var captureSessionId = UUID.randomUUID();
        var captureSession = new CaptureSessionDTO();
        captureSession.setId(captureSessionId);
        captureSession.setStatus(RecordingStatus.STANDBY);
        captureSession.setBookingId(UUID.randomUUID());

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.checkLiveFeedAvailable(captureSessionId)).thenReturn(false);
        when(captureSessionService.findById(captureSessionId)).thenReturn(captureSession);
        when(azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString()))
            .thenReturn(false);

        mockMvc.perform(put("/media-service/streaming-locator/live-event/" + captureSessionId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Asset for capture session: " + captureSessionId + " found with no .ism file"));


        verify(azureIngestStorageService, times(1))
            .doesIsmFileExist(captureSession.getBookingId().toString());
        verify(mediaService, never()).playLiveEvent(any());
    }

    @Test
    @DisplayName("Should return a 404 when the source container doesn't exist")
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
               .andExpect(jsonPath("$.message")
                              .value("Not found: Source Container: " + generateAssetDTO.getSourceContainer()));
    }

    @Test
    @DisplayName("Should return a 404 when the source blob doesn't exist")
    void generateAsset404NoSourceBlob() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID() + "-input");
        generateAssetDTO.setDestinationContainer(UUID.randomUUID());
        generateAssetDTO.setTempAsset("blobby");
        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenReturn(true);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer()))
            .thenThrow(new NotFoundException("No files ending .mp4 were found in the Source Container "
                                                 + generateAssetDTO.getSourceContainer()));
        mockMvc.perform(post("/media-service/generate-asset")
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(generateAssetDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
               .andExpect(status().isNotFound())
               .andExpect(jsonPath("$.message")
                              .value("Not found: No files ending .mp4 were found in the Source Container "
                                         + generateAssetDTO.getSourceContainer()));
    }

    @Test
    @DisplayName("Should return a 400 when incorrect body provided")
    void generateAssetTest400Error() throws Exception {
        var response = mockMvc.perform(post("/media-service/generate-asset"))
                              .andExpect(status().is4xxClientError())
                              .andReturn().getResponse();
        assertThat(response.getContentAsString()).contains(
            "Required request body is missing: public org.springframework.http.ResponseEntity"
                + "<uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO>");
    }

    @Test
    @DisplayName("Should return a 400 when incorrect source container name provided")
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
        assertThat(response.getContentAsString()).contains("{\"sourceContainer\":"
                + "\"must match \\\"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-input$\\\"\"}"
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should return a GenerateAssetResponseDTO successfully")
    void generateAssetTest200() throws Exception {
        var generateAssetDTO = new GenerateAssetDTO();
        generateAssetDTO.setSourceContainer(UUID.randomUUID() + "-input");
        generateAssetDTO.setDestinationContainer(UUID.randomUUID());
        generateAssetDTO.setTempAsset("blobby");
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())).thenReturn(true);
        when(azureFinalStorageService.doesContainerExist(generateAssetDTO.getDestinationContainer().toString()))
            .thenReturn(true);
        when(azureFinalStorageService.getMp4FileName(generateAssetDTO.getSourceContainer())).thenReturn("blobby.mp4");
        when(azureFinalStorageService.getMp4FileName(generateAssetDTO.getDestinationContainer().toString()))
            .thenReturn("something-else.mp4");

        when(mediaServiceBroker.getEnabledMediaService()).thenReturn(mediaService);
        when(mediaService.importAsset(any(GenerateAssetDTO.class), eq(true))).thenReturn(
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
