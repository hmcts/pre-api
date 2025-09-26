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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
    @DisplayName("Should return correct duration when valid output is provided")
    void getDurationFromMp4Success() {
        String containerName = "container-name";
        String fileName = "file-name";
        String mockOutput = "123.456";

        when(azureFinalStorageService.downloadBlob(containerName, fileName, fileName))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn(mockOutput);

        Duration duration = ffmpegService.getDurationFromMp4(containerName, fileName);

        assertThat(duration).isNotNull();
        assertThat(duration.toMillis()).isEqualTo(123456);
    }

    @Test
    @DisplayName("Should return null when ffprobe output is non-numeric")
    void getDurationFromMp4InvalidOutput() {
        String containerName = "container-name";
        String fileName = "file-name";
        when(azureFinalStorageService.downloadBlob(containerName, fileName, fileName))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn("not_a_number");

        Duration duration = ffmpegService.getDurationFromMp4(containerName, fileName);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should return null when exception is thrown during command execution")
    void getDurationFromMp4ThrowsException() {
        String containerName = "container-name";
        String fileName = "file-name";
        when(azureFinalStorageService.downloadBlob(containerName, fileName, fileName))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class)))
            .thenThrow(new RuntimeException("An error occurred"));

        Duration duration = ffmpegService.getDurationFromMp4(containerName, fileName);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should return null when ffprobe returns empty output")
    void getDurationFromMp4EmptyOutput() {
        String containerName = "container-name";
        String fileName = "file-name";
        when(azureFinalStorageService.downloadBlob(containerName, fileName, fileName))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class))).thenReturn("  ");

        Duration duration = ffmpegService.getDurationFromMp4(containerName, fileName);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should throw error when unable to read edit instructions")
    void generateCommandFromJsonError() {
        editRequest.setEditInstruction("");

        String message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Unable to read edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are null")
    void generateCommandFromJsonEmpty() {
        String message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Malformed edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are empty")
    void generateCommandEditInstructionsEmpty() {
        editRequest.setEditInstruction("{\"ffmpegInstructions\": []}");
        String message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Malformed edit instructions");
    }

    @Test
    @DisplayName("Should successfully generate ffmpeg commands")
    void generateCommandSuccess() throws JsonProcessingException {
        List<FfmpegEditInstructionDTO> instructions = setRandomEditInstructions();

        LinkedHashMap<String, CommandLine>
            commands = ffmpegService.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        assertThat(commands).isNotNull();
        assertThat(commands).hasSize(2);
        Map.Entry<String, CommandLine> firstCommandEntry = commands.pollFirstEntry();
        assertThat(firstCommandEntry.getKey()).isEqualTo("0_segment.mp4");
        CommandLine firstCommand = firstCommandEntry.getValue();
        assertThat(firstCommand.getExecutable()).isEqualTo("ffmpeg");

        String[] args1 = firstCommand.getArguments();
        assertThat(args1).hasSize(12);
        assertThat(args1[0]).isEqualTo("-y");
        assertThat(args1[1]).isEqualTo("-ss");
        assertThat(args1[2]).isEqualTo(String.valueOf(instructions.getFirst().getStart()));
        assertThat(args1[3]).isEqualTo("-to");
        assertThat(args1[4]).isEqualTo(String.valueOf(instructions.getFirst().getEnd()));
        assertThat(args1[5]).isEqualTo("-i");
        assertThat(args1[6]).isEqualTo("input.mp4");
        assertThat(args1[7]).isEqualTo("-c");
        assertThat(args1[8]).isEqualTo("copy");
        assertThat(args1[9]).isEqualTo("-avoid_negative_ts");
        assertThat(args1[10]).isEqualTo("1");
        assertThat(args1[11]).isEqualTo("0_segment.mp4");

        Map.Entry<String, CommandLine> secondCommandEntry = commands.pollFirstEntry();
        assertThat(secondCommandEntry.getKey()).isEqualTo("1_segment.mp4");
        CommandLine secondCommand = secondCommandEntry.getValue();
        assertThat(secondCommand.getExecutable()).isEqualTo("ffmpeg");

        String[] args2 = secondCommand.getArguments();
        assertThat(args2).hasSize(12);
        assertThat(args2[0]).isEqualTo("-y");
        assertThat(args2[1]).isEqualTo("-ss");
        assertThat(args2[2]).isEqualTo(String.valueOf(instructions.getLast().getStart()));
        assertThat(args2[3]).isEqualTo("-to");
        assertThat(args2[4]).isEqualTo(String.valueOf(instructions.getLast().getEnd()));
        assertThat(args2[5]).isEqualTo("-i");
        assertThat(args2[6]).isEqualTo("input.mp4");
        assertThat(args2[7]).isEqualTo("-c");
        assertThat(args2[8]).isEqualTo("copy");
        assertThat(args2[9]).isEqualTo("-avoid_negative_ts");
        assertThat(args2[10]).isEqualTo("1");
        assertThat(args2[11]).isEqualTo("1_segment.mp4");
    }

    @Test
    @DisplayName("Should generate a single edit command in multi-command mode")
    void generateSingleEditCommandInMultiCommandMode() {
        editRequest.setEditInstruction("{\"ffmpegInstructions\": [{\"start\": 10, \"end\": 20}]}");
        LinkedHashMap<String, CommandLine> commands =
            ffmpegService.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        assertThat(commands).hasSize(1);
        assertThat(commands).containsKey("output.mp4");
        CommandLine command = commands.get("output.mp4");
        assertThat(command.toString()).contains("-ss", "10", "-to", "20", "-i", "input.mp4", "output.mp4");
    }

    @Test
    @DisplayName("Should generate multiple edit commands and concatenate segments")
    void generateMultipleEditCommandsAndConcatenateSegments() throws JsonProcessingException {
        setRandomEditInstructions();
        LinkedHashMap<String, CommandLine> commands =
            ffmpegService.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        assertThat(commands).hasSizeGreaterThan(1);

        commands.forEach((fileName, command) -> {
            assertThat(fileName).endsWith("_segment.mp4");
            assertThat(command.toString()).contains("-ss", "-to", "-i", "input.mp4", fileName);
        });
    }

    @Test
    @DisplayName("Should fail when execution of a generated command fails")
    void failWhenExecutionOfGeneratedCommandFails() throws JsonProcessingException {
        setRandomEditInstructions();
        UUID newRecordingId = UUID.randomUUID();

        when(azureFinalStorageService.downloadBlob(any(), any(), any())).thenReturn(true);
        when(commandExecutor.execute(any())).thenReturn(false);

        String message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message).isEqualTo(
            "Unknown Server Exception: Error occurred when attempting to process edit request: " + editRequest.getId());

        verify(commandExecutor, times(1)).execute(any());
    }

    @Test
    @DisplayName("Should successfully generate the concat list file")
    void successfullyGenerateConcatListFile() throws IOException {
        Set<String> segmentFiles = Set.of("segment1.mp4", "segment2.mp4");
        ffmpegService.generateConcatListFile(segmentFiles, "concat-list.txt");

        File outputFile = new File("concat-list.txt");
        assertThat(outputFile).exists();
        assertThat(outputFile.length()).isGreaterThan(0);
        outputFile.delete();
    }

    @Test
    @DisplayName("Should successfully execute multi-command ffmpeg flow and upload result")
    void shouldExecuteFullMultiCommandFlowSuccessfully() throws JsonProcessingException {
        setRandomEditInstructions();

        when(azureFinalStorageService.downloadBlob(any(), any(), any())).thenReturn(true);
        when(commandExecutor.execute(any())).thenReturn(true);
        when(azureIngestStorageService.uploadBlob(any(), any(), any())).thenReturn(true);

        UUID newRecordingId = UUID.randomUUID();
        ffmpegService.performEdit(newRecordingId, editRequest);

        // 2 segment commands + 1 concat command = 3 ffmpeg executions
        verify(commandExecutor, times(3)).execute(any(CommandLine.class));
        verify(azureFinalStorageService).downloadBlob(any(), any(), any());
        verify(azureIngestStorageService).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw when concat command fails after successful segment commands")
    void shouldFailWhenConcatCommandFails() throws JsonProcessingException {
        setRandomEditInstructions();
        UUID newRecordingId = UUID.randomUUID();

        when(azureFinalStorageService.downloadBlob(any(), any(), any())).thenReturn(true);

        when(commandExecutor.execute(any()))
            .thenReturn(true) // segment 0
            .thenReturn(true) // segment 1
            .thenReturn(false); // concat

        String message = assertThrows(
            UnknownServerException.class,
            () -> ffmpegService.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to process edit request: "
                           + editRequest.getId());

        verify(commandExecutor, times(3)).execute(any()); // 2 segments + 1 concat
        verify(azureIngestStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should pass correct segment file names to concat list generator")
    void shouldPassCorrectFilesToConcatListFile() throws IOException {
        var instructions = setRandomEditInstructions();
        var commands = ffmpegService.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        var concatListFile = new File("test-concat-list.txt");
        ffmpegService.generateConcatListFile(commands.keySet(), concatListFile.getName());

        var lines = Files.readAllLines(concatListFile.toPath());
        assertThat(lines).hasSize(instructions.size());
        lines.forEach(line -> assertThat(line).startsWith("file '").endsWith(".mp4'"));

        concatListFile.delete();
    }

    @Test
    @DisplayName("Should run single command and upload when only one edit instruction is provided")
    void shouldRunSingleCommandWhenOneInstructionProvided() {
        editRequest.setEditInstruction("{\"ffmpegInstructions\": [{\"start\": 5, \"end\": 15}]}");

        when(azureFinalStorageService.downloadBlob(any(), any(), any())).thenReturn(true);
        when(commandExecutor.execute(any())).thenReturn(true);
        when(azureIngestStorageService.uploadBlob(any(), any(), any())).thenReturn(true);

        UUID newRecordingId = UUID.randomUUID();
        ffmpegService.performEdit(newRecordingId, editRequest);

        // Only one command executed (no concat)
        verify(commandExecutor, times(1)).execute(any());

        verify(azureFinalStorageService, times(1)).downloadBlob(any(), any(), any());
        verify(azureIngestStorageService, times(1)).uploadBlob(any(), any(), any());
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
