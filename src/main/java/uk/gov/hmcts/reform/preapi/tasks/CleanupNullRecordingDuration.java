package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.time.Duration;

@Slf4j
@Component
public class CleanupNullRecordingDuration extends RobotUserTask {

    private final RecordingService recordingService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final FfmpegService ffmpegService;

    @Autowired
    protected CleanupNullRecordingDuration(UserService userService,
                                           UserAuthenticationService userAuthenticationService,
                                           RecordingService recordingService,
                                           AzureFinalStorageService azureFinalStorageService,
                                           @Value("${cron-user-email}") String cronUserEmail,
                                           FfmpegService ffmpegService) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.recordingService = recordingService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.ffmpegService = ffmpegService;
    }

    @Override
    public void run() {
        signInRobotUser();

        log.info("Starting CleanupNullRecordingDuration task");
        recordingService.findAllDurationNull()
            .stream()
            .filter(this::hasStorageContainer)
            .map(this::createRecordingDTO)
            .peek(this::tryGetDurationFromMetadata)
            .filter(this::updateRecording)
            .filter(this::ensureFilenameExists)
            .peek(this::tryGetDurationFromFfmpeg)
            .filter(this::updateRecording)
            .forEach(dto -> log.error("Unable to get duration for recording {}", dto.getId()));
    }

    private CreateRecordingDTO createRecordingDTO(RecordingDTO recording) {
        final CreateRecordingDTO dto = new CreateRecordingDTO();
        dto.setId(recording.getId());
        dto.setCaptureSessionId(recording.getCaptureSession().getId());
        dto.setParentRecordingId(recording.getParentRecordingId());
        dto.setVersion(recording.getVersion());
        dto.setFilename(recording.getFilename());
        dto.setEditInstructions(recording.getEditInstructions());
        return dto;
    }

    private boolean hasStorageContainer(final RecordingDTO dto) {
        boolean hasContainer = azureFinalStorageService.doesContainerExist(dto.getId().toString());
        if (!hasContainer) {
            log.info("No container found for recording {}, it will be skipped", dto.getId());
        }
        return hasContainer;
    }

    private void tryGetDurationFromMetadata(final CreateRecordingDTO dto) {
        log.info("Attempting to get duration for recording {} from metadata", dto.getId());
        final Duration duration = azureFinalStorageService.getRecordingDuration(dto.getId());
        if (duration != null) {
            dto.setDuration(duration);
        }
    }

    private boolean ensureFilenameExists(final CreateRecordingDTO dto) {
        if (dto.getFilename() != null
            && azureFinalStorageService.doesBlobExist(dto.getId().toString(), dto.getFilename())) {
            return true;
        }

        log.info("Invalid filename found for recording {} {}", dto.getId(), dto.getFilename());
        try {
            final String filename = azureFinalStorageService.getMp4FileName(dto.getId().toString());
            dto.setFilename(filename);

            log.info("Updating recording {} filename to {}", dto.getId(), filename);
            return true;
        } catch (NotFoundException e) {
            log.info("Recording {} has no mp4 and has been skipped", dto.getId());
        }
        return false;
    }

    private void tryGetDurationFromFfmpeg(final CreateRecordingDTO dto) {
        log.info("Attempting to get duration for recording {} from mp4: {}", dto.getId(), dto.getFilename());

        try {
            final String sasToken = azureFinalStorageService.generateReadSasUrl(
                dto.getId().toString(),
                dto.getFilename());
            final Duration duration = ffmpegService.getDurationFromMp4ViaSasToken(sasToken);
            dto.setDuration(duration);
        } catch (Exception e) {
            log.info("Failed to get duration from MP4 for recording {}: {}", dto.getId(), e.getMessage());
        }
    }

    private boolean updateRecording(final CreateRecordingDTO dto) {
        if (dto.getDuration() == null) {
            return true;
        }
        log.info("Updating duration for recording {} to {}", dto.getId(), dto.getDuration());
        recordingService.upsert(dto);
        return false;
    }
}
