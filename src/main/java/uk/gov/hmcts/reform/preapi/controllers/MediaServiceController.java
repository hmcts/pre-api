package uk.gov.hmcts.reform.preapi.controllers;

import com.azure.resourcemanager.mediaservices.models.JobState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.EncodeJobDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.GenerateAssetResponseDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.dto.media.PlaybackDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.EncodeTransform;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.AssetFilesNotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.exception.UnprocessableContentException;
import uk.gov.hmcts.reform.preapi.media.MediaServiceBroker;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;
import uk.gov.hmcts.reform.preapi.services.EncodeJobService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;

import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@RestController
@RequestMapping("/media-service")
@Log4j2
public class MediaServiceController extends PreApiController {

    private final MediaServiceBroker mediaServiceBroker;
    private final CaptureSessionService captureSessionService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final AzureIngestStorageService azureIngestStorageService;
    private final RecordingService recordingService;
    private final EncodeJobService encodeJobService;

    private final boolean enableEnhancedProcessing;

    @Autowired
    public MediaServiceController(MediaServiceBroker mediaServiceBroker,
                                  CaptureSessionService captureSessionService,
                                  RecordingService recordingService,
                                  AzureFinalStorageService azureFinalStorageService,
                                  AzureIngestStorageService azureIngestStorageService,
                                  EncodeJobService encodeJobService,
                                  @Value("${feature-flags.enable-enhanced-processing:}")
                                      Boolean enableEnhancedProcessing) {
        super();
        this.mediaServiceBroker = mediaServiceBroker;
        this.captureSessionService = captureSessionService;
        this.recordingService = recordingService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.azureIngestStorageService = azureIngestStorageService;
        this.encodeJobService = encodeJobService;
        this.enableEnhancedProcessing = enableEnhancedProcessing;
    }

    @GetMapping("/health")
    @Operation(operationId = "mediaServiceHealth", summary = "Check the status of the media service connection")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<String> mediaService() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        mediaService.getAssets();
        return ResponseEntity.ok("successfully connected to media service ("
                                     + mediaService.getClass().getSimpleName()
                                     + ")");
    }

    @GetMapping("/assets")
    @Operation(operationId = "getAssets", summary = "Get all media service assets")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<AssetDTO>> getAssets() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        return ResponseEntity.ok(mediaService.getAssets());
    }

    @GetMapping("/assets/{assetName}")
    @Operation(operationId = "getAssetsByName", summary = "Get a media service asset by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<AssetDTO> getAsset(@PathVariable String assetName) {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var data = mediaService.getAsset(assetName);
        if (data == null) {
            throw new NotFoundException("Asset: " + assetName);
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/live-events")
    @Operation(operationId = "getLiveEvents", summary = "Get a list of media service live events")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<List<LiveEventDTO>> getLiveEvents() {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        return ResponseEntity.ok(mediaService.getLiveEvents());
    }

    @GetMapping("/live-events/{liveEventName}")
    @Operation(operationId = "getLiveEventsByName", summary = "Get a media service live event by name")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<LiveEventDTO> getLiveEvents(@PathVariable String liveEventName) {
        var mediaService = mediaServiceBroker.getEnabledMediaService();
        var data = mediaService.getLiveEvent(liveEventName);
        if (data == null) {
            throw new NotFoundException("Live event: " + liveEventName);
        }
        if (data.getResourceState().equals("Running") && data.getInputRtmp() != null) {
            try {
                var captureSession = captureSessionService.findByLiveEventId(liveEventName);
                if (captureSession.getStatus() == RecordingStatus.INITIALISING) {
                    captureSessionService.startCaptureSession(
                        captureSession.getId(),
                        RecordingStatus.STANDBY,
                        data.getInputRtmp()
                    );
                }
            } catch (NotFoundException e) {
                // ignore
            }
        }
        return ResponseEntity.ok(data);
    }

    @GetMapping("/vod")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3')")
    public ResponseEntity<PlaybackDTO> getVod(
        @RequestParam UUID recordingId,
        @RequestParam(required = false) String mediaService
    ) throws InterruptedException {
        // check recording exists + authed
        var recording = recordingService.findById(recordingId);

        if (recording.getCaptureSession().getCaseState() == CaseState.CLOSED) {
            throw new ResourceInWrongStateException("Case associated with Recording("
                                                        + recordingId
                                                        + ") is in state CLOSED. Cannot play recording.");
        }

        var service = mediaServiceBroker.getEnabledMediaService(mediaService);

        // TODO dont rely on naming convention, link asset name in db
        var assetName = recordingId.toString().replace("-", "") + "_output";
        var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();

        return ResponseEntity.ok(service.playAsset(assetName, userId.toString()));
    }

    @PutMapping("/live-event/end/{captureSessionId}")
    @Operation(operationId = "stopLiveEvent", summary = "Stop a live event")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2')")
    public ResponseEntity<CaptureSessionDTO> stopLiveEvent(
        @PathVariable UUID captureSessionId
    ) throws InterruptedException {
        var dto = captureSessionService.findById(captureSessionId);

        if (dto.getFinishedAt() != null) {
            return ResponseEntity.ok(dto);
        }

        if (dto.getStartedAt() == null) {
            throw new ResourceInWrongStateException("Resource: Capture Session("
                                                        + captureSessionId
                                                        + ") has not been started.");
        }

        if (dto.getStatus() != RecordingStatus.STANDBY && dto.getStatus() != RecordingStatus.RECORDING) {
            throw new ResourceInWrongStateException(
                "Capture Session",
                captureSessionId.toString(),
                dto.getStatus().toString(),
                "STANDBY or RECORDING"
            );
        }

        var recordingId = UUID.randomUUID();
        if (!enableEnhancedProcessing) {
            // todo code to removed once feature fully enabled (deprecated)
            dto = captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.PROCESSING, recordingId);
        }

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        try {
            if (!enableEnhancedProcessing) {
                // todo code to removed once feature fully enabled (deprecated)
                var status = mediaService.stopLiveEventAndProcess(dto, recordingId);
                if (status == RecordingStatus.FAILURE) {
                    throw new UnknownServerException("Encountered an error during encoding process for CaptureSession("
                                                         + captureSessionId
                                                         + ")");
                }
                dto = captureSessionService.stopCaptureSession(captureSessionId, status, recordingId);
            } else {
                mediaService.stopLiveEvent(dto, recordingId);
                var jobName = mediaService.triggerProcessingStep1(
                    dto,
                    dto.getId().toString().replace("-", ""),
                    recordingId
                );
                if (jobName == null) {
                    dto =
                        captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.NO_RECORDING, null);
                } else {
                    dto = captureSessionService.stopCaptureSession(
                        captureSessionId,
                        RecordingStatus.PROCESSING,
                        recordingId
                    );
                    var encodeJob = new EncodeJobDTO();
                    encodeJob.setId(UUID.randomUUID());
                    encodeJob.setCaptureSessionId(captureSessionId);
                    encodeJob.setJobName(jobName);
                    encodeJob.setRecordingId(recordingId);
                    encodeJob.setTransform(EncodeTransform.ENCODE_FROM_INGEST);
                    encodeJobService.upsert(encodeJob);
                }
            }
        } catch (Exception e) {
            captureSessionService.stopCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
            throw e;
        }

        return ResponseEntity.ok(dto);
    }

    @PutMapping("/streaming-locator/live-event/{captureSessionId}")
    @Operation(operationId = "playLiveEvent", summary = "Play a live event")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> createLiveEventStreamingLocator(@PathVariable UUID captureSessionId)
        throws InterruptedException {
        // load captureSession
        var captureSession = captureSessionService.findById(captureSessionId);
        Logger.getAnonymousLogger().info("createLiveEventStreamingLocator: " + captureSession);

        // return existing captureSession if currently live
        if (captureSession.getLiveOutputUrl() != null && captureSession.getStatus() == RecordingStatus.RECORDING) {
            return ResponseEntity.ok(captureSession);
        }
        Logger.getAnonymousLogger().info("captureSession getStatus: " + captureSession.getStatus());
        Logger.getAnonymousLogger().info("captureSession getLiveOutputUrl: " + captureSession.getLiveOutputUrl());

        // check if captureSession is in correct state
        if (captureSession.getStatus() != RecordingStatus.STANDBY
            && captureSession.getStatus() != RecordingStatus.RECORDING) {
            throw new ResourceInWrongStateException(captureSession.getClass().getSimpleName(),
                                                    captureSessionId.toString(),
                                                    captureSession.getStatus().name(),
                                                    RecordingStatus.STANDBY.name());
        }
        var container = captureSession.getBookingId().toString();
        if (!azureIngestStorageService.doesIsmFileExist(container)
            && !azureIngestStorageService.doesBlobExist(container, "gc_state")) {
            throw new AssetFilesNotFoundException(captureSessionId);
        }

        // play live event
        var liveOutputUrl = mediaServiceBroker.getEnabledMediaService().playLiveEvent(captureSessionId);

        // update captureSession
        captureSession.setLiveOutputUrl(liveOutputUrl);
        captureSession.setStatus(RecordingStatus.RECORDING);
        captureSessionService.upsert(captureSession);

        return ResponseEntity.ok(captureSession);
    }

    @PostMapping("/live-event/check/{captureSessionId}")
    @Operation(
        operationId = "checkStream",
        summary = "Check stream has started"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> checkStream(@PathVariable UUID captureSessionId) {
        var captureSession = captureSessionService.findById(captureSessionId);
        if (captureSession.getStatus() == RecordingStatus.RECORDING) {
            return ResponseEntity.ok(captureSession);
        }

        if (captureSession.getFinishedAt() != null) {
            log.info("Resource: Capture Session: {} has already finished.", captureSessionId);
            return ResponseEntity.ok(captureSession);
        }

        if (captureSession.getStartedAt() == null) {
            throw new UnprocessableContentException("Resource: Capture Session("
                                                          + captureSessionId
                                                          + ") has not been started.");
        }

        if (captureSession.getStatus() != RecordingStatus.STANDBY) {
            throw new UnprocessableContentException("Resource: Capture Session("
                                                        + captureSessionId
                                                        + ") is in a "
                                                        + captureSession.getStatus()
                                                        + " state. Expected state is STANDBY.");
        }

        if (azureIngestStorageService.doesIsmFileExist(captureSession.getBookingId().toString())
            || azureIngestStorageService.doesBlobExist(captureSession.getBookingId().toString(), "gc_state")) {
            return ResponseEntity.ok(captureSessionService
                                         .setCaptureSessionStatus(captureSessionId, RecordingStatus.RECORDING));
        }
        throw new NotFoundException("No stream found");
    }

    @PutMapping("/live-event/start/{captureSessionId}")
    @Operation(operationId = "startLiveEvent", summary = "Start a live event")
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<CaptureSessionDTO> startLiveEvent(@PathVariable UUID captureSessionId) {
        var dto = captureSessionService.findById(captureSessionId);

        if (dto.getCaseState() != CaseState.OPEN) {
            throw new ResourceInWrongStateException(
                "Capture Session",
                dto.getId(),
                dto.getCaseState(),
                "OPEN"
            );
        }

        if (dto.getStatus() == RecordingStatus.FAILURE) {
            throw new ResourceInWrongStateException(
                "Capture Session",
                dto.getId().toString(),
                dto.getStatus().toString(),
                RecordingStatus.INITIALISING.toString()
            );
        }

        if (dto.getFinishedAt() != null) {
            throw new ConflictException("Capture Session: " + dto.getId() + " has already been finished");
        }

        if (dto.getStartedAt() != null) {
            return ResponseEntity.ok(dto);
        }

        var mediaService = mediaServiceBroker.getEnabledMediaService();
        try {
            mediaService.startLiveEvent(dto);
        } catch (Exception e) {
            captureSessionService.startCaptureSession(captureSessionId, RecordingStatus.FAILURE, null);
            throw e;
        }

        return ResponseEntity.ok(captureSessionService.startCaptureSession(
            captureSessionId,
            RecordingStatus.INITIALISING,
            null
        ));
    }

    @PostMapping("/generate-asset")
    @Operation(
        operationId = "generateAsset",
        summary = "LEGACY - Given a source & destination, this endpoint will generate a streaming asset for a given mp4"
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1')")
    public ResponseEntity<GenerateAssetResponseDTO> generateAsset(
        @Parameter(hidden = true) String code,
        @RequestBody @Valid GenerateAssetDTO generateAssetDTO
    ) throws InterruptedException {

        if (!azureFinalStorageService.doesContainerExist(generateAssetDTO.getSourceContainer())) {
            throw new NotFoundException("Source Container: " + generateAssetDTO.getSourceContainer());
        }

        // 404s of there are no mp4 files in the container
        // this is called in the MK process anyway but AMS doesn't need the specific filename
        azureFinalStorageService.getMp4FileName(generateAssetDTO.getSourceContainer());

        log.info("Attempting to generate asset: {}", generateAssetDTO);

        var result = mediaServiceBroker.getEnabledMediaService().importAsset(generateAssetDTO, true);
        if (result.getJobStatus().equals(JobState.FINISHED.toString())) {
            // add new version to recording etc
            var parentRecording = recordingService.findById(generateAssetDTO.getParentRecordingId());

            var recording = new CreateRecordingDTO();
            recording.setId(generateAssetDTO.getNewRecordingId());
            recording.setParentRecordingId(parentRecording.getId());
            recording.setCaptureSessionId(parentRecording.getCaptureSession().getId());

            var search = new SearchRecordings();
            search.setCaptureSessionId(parentRecording.getCaptureSession().getId());
            var numRecordingsForCaptureSession = recordingService.findAll(
                search,
                true,
                Pageable.unpaged()
            ).getSize();
            recording.setVersion(numRecordingsForCaptureSession + 1);
            // filename is used by editing process
            recording.setFilename(
                azureFinalStorageService.getMp4FileName(generateAssetDTO.getDestinationContainer().toString())
            );
            recordingService.upsert(recording);
            return ResponseEntity.ok(result);
        }

        return ResponseEntity.internalServerError().body(result);
    }

    @GetMapping("/blob/{containerName}")
    @Operation(
        operationId = "checkBlobExists",
        summary = "Checks if a container contains the .ism file. 204 on success, 404 on failure."
    )
    @PreAuthorize("hasAnyRole('ROLE_SUPER_USER', 'ROLE_LEVEL_1', 'ROLE_LEVEL_2', 'ROLE_LEVEL_3', 'ROLE_LEVEL_4')")
    public ResponseEntity<Boolean> checkBlobExists(@PathVariable String containerName) {
        return azureFinalStorageService.doesIsmFileExist(containerName)
            ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }
}
