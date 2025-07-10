package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = FfmpegService.class)
public class FfmpegServiceTest {
    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private CommandExecutor commandExecutor;

    @Autowired
    private FfmpegService ffmpegService;

    private Recording recording;
    private EditRequest editRequest;

    @BeforeEach
    void setUp() {
        recording = new Recording();
        recording.setId(UUID.randomUUID());
        recording.setFilename("input.mp4");
        recording.setDuration(Duration.ofMinutes(3));

        editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(recording);
        editRequest.setEditInstruction("{}");
        editRequest.setStatus(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw error when unable to read edit instructions")
    void generateCommandFromJsonError() {
        editRequest.setEditInstruction("");

        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateCommand(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Unable to read edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are null")
    void generateCommandFromJsonEmpty() {
        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateCommand(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Malformed edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are empty")
    void generateCommandEditInstructionsEmpty() {
        editRequest.setEditInstruction("{\"ffmpegInstructions\": []}");
        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateCommand(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Malformed edit instructions");
    }

    @Test
    @DisplayName("Should successfully generate ffmpeg command")
    void generateCommandSuccess() throws JsonProcessingException {
        var instructions = setRandomEditInstructions();

        var command = ffmpegService.generateCommand(editRequest, "input.mp4", "output.mp4");

        assertThat(command).isNotNull();
        assertThat(command.getExecutable()).isEqualTo("ffmpeg");

        var args = command.getArguments();
        assertThat(args).hasSize(14);
        assertThat(args[0]).isEqualTo("-y");
        assertThat(args[1]).isEqualTo("-i");
        assertThat(args[2]).isEqualTo("input.mp4");
        assertThat(args[3]).isEqualTo("-filter_complex");
        var filterComplexStr = args[4];
        // check for generated commands for first instruction
        assertThat(filterComplexStr).contains(String.format("[0:v]trim=start=%d:end=%d,setpts=PTS-STARTPTS[v1];",
                                                            instructions.getFirst().getStart(),
                                                            instructions.getFirst().getEnd()));
        assertThat(filterComplexStr).contains(String.format("[0:a]atrim=start=%d:end=%d,asetpts=PTS-STARTPTS[a1];",
                                                            instructions.getFirst().getStart(),
                                                            instructions.getFirst().getEnd()));
        // check for generated commands for second instruction
        assertThat(filterComplexStr).contains(String.format("[0:v]trim=start=%d:end=180,setpts=PTS-STARTPTS[v2];",
                                                            instructions.getLast().getStart()));
        assertThat(filterComplexStr).contains(String.format("[0:a]atrim=start=%d:end=180,asetpts=PTS-STARTPTS[a2];",
                                                            instructions.getLast().getStart()));
        // check for concat
        assertThat(filterComplexStr).contains("[v1][a1][v2][a2]concat=n=2:v=1:a=1[outv][outa]");

        // check concat outputs and using correct codecs etc
        assertThat(args[5]).isEqualTo("-map");
        assertThat(args[6]).isEqualTo("[outv]");
        assertThat(args[7]).isEqualTo("-map");
        assertThat(args[8]).isEqualTo("[outa]");
        assertThat(args[9]).isEqualTo("-c:v");
        assertThat(args[10]).isEqualTo("libx264");
        assertThat(args[11]).isEqualTo("-c:a");
        assertThat(args[12]).isEqualTo("aac");
        assertThat(args[13]).isEqualTo("output.mp4");
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a filename set")
    void performEditFileNameNull() {
        recording.setFilename(null);
        var newRecordingId = UUID.randomUUID();

        var message = assertThrows(
            NotFoundException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: No file name provided");

        verify(azureFinalStorageService,never()).downloadBlob(any(), any(), any());
        verify(commandExecutor, never()).execute(any());
        verify(azureIngestStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a filename set")
    void performEditDownloadFailure() throws JsonProcessingException {
        setRandomEditInstructions();
        var newRecordingId = UUID.randomUUID();
        var inputFileName = editRequest.getSourceRecording().getFilename();
        var containerName = editRequest.getSourceRecording().getId().toString();

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(false);

        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to download file: "
                           + editRequest.getSourceRecording().getFilename());

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName, inputFileName);
        verify(commandExecutor, never()).execute(any());
        verify(azureIngestStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw error when ffmpeg execution encounters and error")
    void performEditExecuteFailure() throws JsonProcessingException {
        setRandomEditInstructions();
        var newRecordingId = UUID.randomUUID();
        var inputFileName = editRequest.getSourceRecording().getFilename();
        var containerName = editRequest.getSourceRecording().getId().toString();

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(true);
        when(commandExecutor.execute(any(CommandLine.class))).thenReturn(false);

        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to process edit request: "
                           + editRequest.getId());

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName, inputFileName);
        verify(commandExecutor, times(1)).execute(any(CommandLine.class));
        verify(azureIngestStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw error when upload to storage encounters an error")
    void performEditUploadError() throws JsonProcessingException {
        setRandomEditInstructions();
        var newRecordingId = UUID.randomUUID();
        var inputFileName = editRequest.getSourceRecording().getFilename();
        var containerName = editRequest.getSourceRecording().getId().toString();
        var outputFileName = newRecordingId + ".mp4";
        var outputContainerName = newRecordingId + "-input";

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(true);
        when(commandExecutor.execute(any(CommandLine.class))).thenReturn(true);
        when(azureIngestStorageService.uploadBlob(outputFileName, outputContainerName, outputFileName))
            .thenReturn(false);

        var message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Error occurred when attempting to upload file");

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName, inputFileName);
        verify(commandExecutor, times(1)).execute(any(CommandLine.class));
        verify(azureIngestStorageService, times(1)).uploadBlob(outputFileName, outputContainerName, outputFileName);
    }

    @Test
    @DisplayName("Should apply edit instructions and upload to storage account")
    void performEditSuccess() throws JsonProcessingException {
        setRandomEditInstructions();
        var newRecordingId = UUID.randomUUID();
        var inputFileName = editRequest.getSourceRecording().getFilename();
        var containerName = editRequest.getSourceRecording().getId().toString();
        var outputFileName = newRecordingId + ".mp4";
        var outputContainerName = newRecordingId + "-input";

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(true);
        when(commandExecutor.execute(any(CommandLine.class))).thenReturn(true);
        when(azureIngestStorageService.uploadBlob(outputFileName, outputContainerName, outputFileName))
            .thenReturn(true);

        ffmpegService.performEdit(newRecordingId, editRequest);

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName, inputFileName);
        verify(commandExecutor, times(1)).execute(any(CommandLine.class));
        verify(azureIngestStorageService, times(1)).uploadBlob(outputFileName, outputContainerName, outputFileName);
    }

    @Test
    @DisplayName("Should return correct duration when valid output is provided")
    void getDurationFromMp4ViaUrlSuccess() {
        String sasToken = "https://example.com/video.mp4?token";
        String mockOutput = "123.456";

        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn(mockOutput);

        Duration duration = ffmpegService.getDurationFromMp4ViaUrl(sasToken);

        assertThat(duration).isNotNull();
        assertThat(duration.toMillis()).isEqualTo(123456);
    }

    @Test
    @DisplayName("Should return null when ffprobe output is non-numeric")
    void getDurationFromMp4ViaUrlInvalidOutput() {
        String sasToken = "https://example.com/video.mp4?token";
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn("not_a_number");

        Duration duration = ffmpegService.getDurationFromMp4ViaUrl(sasToken);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should return null when exception is thrown during command execution")
    void getDurationFromMp4ViaUrlThrowsException() {
        String sasToken = "https://example.com/video.mp4?token";
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class)))
            .thenThrow(new RuntimeException("An error occurred"));

        Duration duration = ffmpegService.getDurationFromMp4ViaUrl(sasToken);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should return null when ffprobe returns empty output")
    void getDurationFromMp4ViaUrlEmptyOutput() {
        String sasToken = "https://example.com/video.mp4?token";
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn("  ");

        Duration duration = ffmpegService.getDurationFromMp4ViaUrl(sasToken);

        assertThat(duration).isNull();
    }

    private String generateEditInstructionsJson(List<FfmpegEditInstructionDTO> ffmpegEditInstructions)
        throws JsonProcessingException {
        var objectMapper = new ObjectMapper();
        var instructions = new EditInstructions(List.of(), ffmpegEditInstructions);

        return objectMapper.writeValueAsString(instructions);
    }

    private List<FfmpegEditInstructionDTO> setRandomEditInstructions() throws JsonProcessingException {
        var random = new Random();
        var instructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(random.nextInt(0, 10))
                .end(random.nextInt(60, 70))
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(random.nextInt(120, 130))
                .end(180)
                .build()
        );
        editRequest.setEditInstruction(generateEditInstructionsJson(instructions));
        return instructions;
    }
}
