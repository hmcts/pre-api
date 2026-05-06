package uk.gov.hmcts.reform.preapi.services.edit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.exec.CommandLine;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
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
import static uk.gov.hmcts.reform.preapi.services.edit.EditRequestTestUtils.assertEditInstructionsEq;
import static uk.gov.hmcts.reform.preapi.utils.JsonUtils.toJson;

@SpringBootTest(classes = FfmpegService.class)
public class FfmpegServiceTest {
    @MockitoBean
    private AzureIngestStorageService azureIngestStorageService;

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    private CommandExecutor commandExecutor;

    @Autowired
    private FfmpegService underTest;

    @MockitoBean
    private Recording mockRecording;

    @MockitoBean
    private Recording mockParentRecording;

    private EditRequest editRequest;

    @BeforeEach
    void setUp() {
        when(mockRecording.getId()).thenReturn(UUID.randomUUID());
        when(mockRecording.getFilename()).thenReturn("input.mp4");
        when(mockRecording.getDuration()).thenReturn(Duration.ofMinutes(3));

        when(mockParentRecording.getId()).thenReturn(UUID.randomUUID());
        when(mockParentRecording.getFilename()).thenReturn("parent.mp4");
        when(mockParentRecording.getDuration()).thenReturn(Duration.ofMinutes(30));

        editRequest = new EditRequest();
        editRequest.setId(UUID.randomUUID());
        editRequest.setSourceRecording(mockRecording);
        editRequest.setEditInstruction("{}");
        editRequest.setStatus(EditRequestStatus.PENDING);
    }

    @Test
    @DisplayName("Should throw error when source recording does not have a filename set")
    void performEditFileNameNull() {
        when(mockRecording.getFilename()).thenReturn(null);
        UUID newRecordingId = UUID.randomUUID();

        String message = assertThrows(
            NotFoundException.class,
            () -> underTest.performEdit(newRecordingId, editRequest)
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
        UUID newRecordingId = UUID.randomUUID();
        String inputFileName = editRequest.getSourceRecording().getFilename();
        String containerName = editRequest.getSourceRecording().getId().toString();

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(false);

        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to download file: "
                           + editRequest.getSourceRecording().getFilename());

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName,
                inputFileName);
        verify(commandExecutor, never()).execute(any());
        verify(azureIngestStorageService, never()).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw error when ffmpeg execution encounters and error")
    void performEditExecuteFailure() throws JsonProcessingException {
        setRandomEditInstructions();
        UUID newRecordingId = UUID.randomUUID();
        String inputFileName = editRequest.getSourceRecording().getFilename();
        String containerName = editRequest.getSourceRecording().getId().toString();

        when(azureFinalStorageService.downloadBlob(containerName, inputFileName, inputFileName))
            .thenReturn(true);
        when(commandExecutor.execute(any(CommandLine.class))).thenReturn(false);

        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Unknown Server Exception: Error occurred when attempting to process edit request: "
                           + editRequest.getId());

        verify(azureFinalStorageService, times(1)).downloadBlob(containerName, inputFileName,
                inputFileName);
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

        Duration duration = underTest.getDurationFromMp4(containerName, fileName);

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

        Duration duration = underTest.getDurationFromMp4(containerName, fileName);

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

        Duration duration = underTest.getDurationFromMp4(containerName, fileName);

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

        Duration duration = underTest.getDurationFromMp4(containerName, fileName);

        assertThat(duration).isNull();
    }

    @Test
    @DisplayName("Should throw error when unable to read edit instructions")
    void generateCommandFromJsonError() {
        editRequest.setEditInstruction("");

        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Unable to read edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are null")
    void generateCommandFromJsonEmpty() {
        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: Malformed edit instructions");
    }

    @Test
    @DisplayName("Should throw error when instructions are empty")
    void generateCommandEditInstructionsEmpty() {
        editRequest.setEditInstruction("{\"ffmpegInstructions\": []}");
        String message = assertThrows(
            UnknownServerException.class,
            () -> underTest.generateMultiEditCommands(editRequest, "", "")
        ).getMessage();

        assertThat(message).isEqualTo("Unknown Server Exception: No edit instructions received for edit request");
    }

    @Test
    @DisplayName("Should successfully generate ffmpeg commands")
    void generateCommandSuccess() throws JsonProcessingException {
        List<FfmpegEditInstructionDTO> instructions = setRandomEditInstructions();

        LinkedHashMap<String, CommandLine>
            commands = underTest.generateMultiEditCommands(editRequest, "input.mp4",
                "output.mp4");

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
            underTest.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        assertThat(commands).hasSize(1);
        assertThat(commands).containsKey("output.mp4");
        CommandLine command = commands.get("output.mp4");
        assertThat(command.toString()).contains("-ss", "10", "-to", "20", "-i", "input.mp4", "output.mp4");
    }

    @Test
    @DisplayName("Should generate a reencode command when force reencode is enabled")
    void generateReencodeCommandInMultiCommandMode() {
        editRequest.setEditInstruction(
            "{\"requestedInstructions\":[],\"ffmpegInstructions\":[],\"forceReencode\":true}"
        );

        LinkedHashMap<String, CommandLine> commands =
            underTest.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

        assertThat(commands).hasSize(1);
        assertThat(commands).containsKey("output.mp4");

        CommandLine command = commands.get("output.mp4");
        assertThat(command.getExecutable()).isEqualTo("ffmpeg");
        assertThat(command.getArguments()).containsExactly(
            "-fflags", "+genpts",
            "-i", "input.mp4",
            "-c:v", "copy",
            "-af", "aresample=async=1",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            "output.mp4"
        );
    }

    @Test
    @DisplayName("Should generate multiple edit commands and concatenate segments")
    void generateMultipleEditCommandsAndConcatenateSegments() throws JsonProcessingException {
        setRandomEditInstructions();
        LinkedHashMap<String, CommandLine> commands =
            underTest.generateMultiEditCommands(editRequest, "input.mp4", "output.mp4");

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
            () -> underTest.performEdit(newRecordingId, editRequest)
        ).getMessage();

        assertThat(message).isEqualTo(
            "Unknown Server Exception: Error occurred when attempting to process edit request: "
                    + editRequest.getId());

        verify(commandExecutor, times(1)).execute(any());
    }

    @Test
    @DisplayName("Should successfully generate the concat list file")
    void successfullyGenerateConcatListFile() throws IOException {
        Set<String> segmentFiles = Set.of("segment1.mp4", "segment2.mp4");
        underTest.generateConcatListFile(segmentFiles, "concat-list.txt");

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
        underTest.performEdit(newRecordingId, editRequest);

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
            () -> underTest.performEdit(newRecordingId, editRequest)
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
        List<FfmpegEditInstructionDTO> instructions = setRandomEditInstructions();
        LinkedHashMap<String, CommandLine> commands = underTest.generateMultiEditCommands(editRequest,
                "input.mp4", "output.mp4");

        File concatListFile = new File("test-concat-list.txt");
        underTest.generateConcatListFile(commands.keySet(), concatListFile.getName());

        List<@NotNull String> lines = Files.readAllLines(concatListFile.toPath());
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
        underTest.performEdit(newRecordingId, editRequest);

        // Only one command executed (no concat)
        verify(commandExecutor, times(1)).execute(any());

        verify(azureFinalStorageService, times(1)).downloadBlob(any(), any(), any());
        verify(azureIngestStorageService, times(1)).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should run a single reencode command and upload when force reencode is provided")
    void shouldRunSingleCommandWhenForceReencodeProvided() {
        editRequest.setEditInstruction(
            "{\"requestedInstructions\":[],\"ffmpegInstructions\":[],\"forceReencode\":true}"
        );

        when(azureFinalStorageService.downloadBlob(any(), any(), any())).thenReturn(true);
        when(commandExecutor.execute(any())).thenReturn(true);
        when(azureIngestStorageService.uploadBlob(any(), any(), any())).thenReturn(true);

        UUID newRecordingId = UUID.randomUUID();
        underTest.performEdit(newRecordingId, editRequest);

        verify(commandExecutor, times(1)).execute(any());
        verify(azureFinalStorageService, times(1)).downloadBlob(any(), any(), any());
        verify(azureIngestStorageService, times(1)).uploadBlob(any(), any(), any());
    }

    @Test
    @DisplayName("Should throw bad request when instruction cuts entire recording")
    void invertInstructionsBadRequestCutToZeroDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
            .start(0L)
            .end(180L)
            .build());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid Instruction: Cannot cut an entire recording: "
                + "Start(00:00:00), End(00:03:00), "
                + "Recording Duration(00:03:00)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction has same value for start and end")
    void invertInstructionsBadRequestStartEndEqual() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
            .start(60L)
            .end(60L)
            .build());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with 0 second duration invalid: "
                + "Start(00:01:00), End(00:01:00)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time is less than start time")
    void invertInstructionsBadRequestEndLTStart() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
            .start(60L)
            .end(50L)
            .build());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction with end time before start time: "
                + "Start(00:01:00), End(00:00:50)");
    }

    @Test
    @DisplayName("Should throw bad request when instruction end time exceeds duration")
    void invertInstructionsBadRequestEndTimeExceedsDuration() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
            .start(60L)
            .end(200L) // duration is 180
            .build());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message)
            .isEqualTo("Invalid instruction: Instruction end time exceeding duration: "
                + "Start(00:01:00), End(00:03:20), "
                + "Recording Duration(00:03:00)");
    }

    @Test
    @DisplayName("Should throw bad request when instructions overlap")
    void invertInstructionsOverlap() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
            .start(10L)
            .end(30L)
            .build());
        instructions.add(EditCutInstructionDTO.builder()
            .start(20L)
            .end(40L)
            .build());

        String message = assertThrows(
            BadRequestException.class,
            () -> underTest.invertInstructions(instructions, mockRecording)
        ).getMessage();

        assertThat(message).isEqualTo("Overlapping instructions: "
            + "Previous End(00:00:30), Current Start(00:00:20)");
    }

    @Test
    @DisplayName("Should return inverted instructions (ordered correctly)")
    void invertInstructionsSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
            .start(60L)
            .end(120L)
            .build());
        instructions1.add(EditCutInstructionDTO.builder()
            .start(150L)
            .end(180L)
            .build());

        List<@NotNull FfmpegEditInstructionDTO> expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(120)
                .end(150)
                .build()
        );

        assertEditInstructionsEq(expectedInvertedInstructions,
            underTest.invertInstructions(instructions1, mockRecording));
    }

    @Test
    @DisplayName("Should return inverted instructions (ordered correctly) when not cutting the end")
    void invertInstructionsNotCuttingEndSuccess() {
        List<EditCutInstructionDTO> instructions1 = new ArrayList<>();
        instructions1.add(EditCutInstructionDTO.builder()
            .start(60L)
            .end(120L)
            .build());
        instructions1.add(EditCutInstructionDTO.builder()
            .start(150L)
            .end(160L)
            .build());

        List<@NotNull FfmpegEditInstructionDTO> expectedInvertedInstructions = List.of(
            FfmpegEditInstructionDTO.builder()
                .start(0)
                .end(60)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(120)
                .end(150)
                .build(),
            FfmpegEditInstructionDTO.builder()
                .start(160)
                .end(180)
                .build()
        );

        assertEditInstructionsEq(expectedInvertedInstructions,
            underTest.invertInstructions(instructions1, mockRecording));
    }

    @Test
    @DisplayName("Should combine instructions correctly with single new command")
    void combineCutsOnOriginalTimelineSingleCommand() {
        List<@NotNull FfmpegEditInstructionDTO> originallyKeptSegments = List.of(createSegment(0, 10),
                createSegment(20, 30));
        List<@NotNull EditCutInstructionDTO> originalCutSegments = List.of(createCut(10, 20,
                "some reason"));
        EditInstructions originalInstructions = new EditInstructions(originalCutSegments, originallyKeptSegments);

        // Cut at 5–8 in the edited timeline mapping to 5–8 in the original timeline
        EditCutInstructionDTO newCut = createCut(5, 8, "test");

        List<EditCutInstructionDTO> result = underTest.combineCutsOnOriginalTimeline(originalInstructions,
                List.of(newCut));

        assertThat(result).hasSize(2);

        assertThat(result.getFirst().getStart()).isEqualTo(5);
        assertThat(result.getFirst().getEnd()).isEqualTo(8);
        assertThat(result.getFirst().getReason()).isEqualTo("test");

        assertThat(result.getLast().getStart()).isEqualTo(10);
        assertThat(result.getLast().getEnd()).isEqualTo(20);
        assertThat(result.getLast().getReason()).isEqualTo("some reason");
    }

    @Test
    @DisplayName("Should combine instructions correctly with cuts across multiple segments")
    void combineCutsOnOriginalTimelineMapsCutThatSpanningMultipleSegments() {
        List<@NotNull FfmpegEditInstructionDTO> originallyKeptSegments = List.of(createSegment(0, 10),
                createSegment(20, 30));
        List<@NotNull EditCutInstructionDTO> originalCutSegments = List.of(createCut(10, 20,
                "some reason"));
        EditInstructions originalInstructions = new EditInstructions(originalCutSegments, originallyKeptSegments);

        // Cut at 8–12 in the edited timeline:
        // - 8–10 -> 8–10 in original (segment 1)
        // - 10–12 -> 20–22 in original (segment 2)
        EditCutInstructionDTO newCut = createCut(8, 12, "test");

        List<EditCutInstructionDTO> result = underTest.combineCutsOnOriginalTimeline(originalInstructions,
                List.of(newCut));

        assertThat(result).hasSize(1);

        assertThat(result.getFirst().getStart()).isEqualTo(8);
        assertThat(result.getFirst().getEnd()).isEqualTo(22);
        assertThat(result.getFirst().getReason()).isEqualTo("test");
    }

    @Test
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination false")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationFalse() {
        // when editing an edit from legacy editing
        when(mockRecording.getFilename()).thenReturn("filename.mp4");
        when(mockRecording.getDuration()).thenReturn(Duration.ofSeconds(30));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(mockRecording.getId());
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(10, 20, "some reason"))));

        EditRequest result = underTest.prepareEditRequestToCreateOrUpdate(request, mockRecording, editRequest);
        assertThat(result.getId()).isEqualTo(request.getId());
        assertThat(result.getSourceRecording().getId()).isEqualTo(mockRecording.getId());
        assertThat(result.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(result.getEditInstruction()).isNotNull();

        EditInstructions editInstructions = EditInstructions.tryFromJson(result.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).hasSize(1);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getStart()).isEqualTo(10);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getEnd()).isEqualTo(20);

        assertThat(editInstructions.getFfmpegInstructions()).isNotNull();
        assertThat(editInstructions.shouldSendNotifications()).isTrue();

        assertEditInstructionsEq(List.of(createSegment(0, 10), createSegment(20, 30)),
                editInstructions.getFfmpegInstructions());
    }

    @Test
    @DisplayName("Should upsert when isOriginalRecordingEdit is false and isInstructionCombination true")
    void upsertIsOriginalRecordingEditFalseIsInstructionCombinationTrue() {
        // when editing an edit from the new editing process
        final EditInstructions originalEdits = new EditInstructions(
                List.of(createCut(10, 20, "some original reason")),
                List.of(createSegment(0, 10), createSegment(20, 30)));

        CreateEditRequestDTO request = new CreateEditRequestDTO();
        request.setId(UUID.randomUUID());
        request.setSourceRecordingId(mockRecording.getId());
        request.setStatus(EditRequestStatus.PENDING);
        request.setEditInstructions(new ArrayList<>(List.of(createCut(5, 8, "some new reason"))));

        when(mockRecording.getParentRecording()).thenReturn(mockParentRecording);
        when(mockRecording.getEditInstruction()).thenReturn(toJson(originalEdits));

        EditRequest result = underTest.prepareEditRequestToCreateOrUpdate(request, mockRecording, editRequest);
        assertThat(result.getId()).isEqualTo(request.getId());
        assertThat(result.getSourceRecording().getId()).isEqualTo(mockParentRecording.getId());
        assertThat(result.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(result.getEditInstruction()).isNotNull();

        EditInstructions editInstructions = EditInstructions.tryFromJson(result.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).hasSize(2);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getStart()).isEqualTo(5);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getEnd()).isEqualTo(8);
        assertThat(editInstructions.getRequestedInstructions().getFirst().getReason())
                .isEqualTo("some new reason");
        assertThat(editInstructions.getRequestedInstructions().getLast().getStart()).isEqualTo(10);
        assertThat(editInstructions.getRequestedInstructions().getLast().getEnd()).isEqualTo(20);
        assertThat(editInstructions.getRequestedInstructions().getLast().getReason())
                .isEqualTo("some original reason");

        assertThat(editInstructions.getFfmpegInstructions()).isNotNull();

        assertEditInstructionsEq(List.of(createSegment(0, 5), createSegment(8, 10), createSegment(20, 1800)),
                editInstructions.getFfmpegInstructions());
    }

    @Test
    @DisplayName("Should create a new edit request with notifications disabled")
    void createEditRequestWithNotificationsDisabledSuccess() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                .start(60L)
                .end(120L)
                .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecording.getId());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);
        dto.setSendNotifications(false);

        EditRequest response = underTest.prepareEditRequestToCreateOrUpdate(dto, mockRecording, editRequest);

        EditInstructions editInstructions = EditInstructions.tryFromJson(response.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.shouldSendNotifications()).isFalse();
    }

    @Test
    @DisplayName("Should update an edit request")
    void updateEditRequestSuccess() {
        List<EditCutInstructionDTO> instructions = new ArrayList<>();
        instructions.add(EditCutInstructionDTO.builder()
                .start(60L)
                .end(120L)
                .build());

        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecording.getId());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstructions(instructions);

        EditRequest response = underTest.prepareEditRequestToCreateOrUpdate(dto, mockRecording, editRequest);

        assertThat(response.getId()).isEqualTo(dto.getId());
        assertThat(response.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(response.getSourceRecording().getId()).isEqualTo(mockRecording.getId());
        assertThat(response.getEditInstruction())
                .contains("\"ffmpegInstructions\":[{\"start\":0,\"end\":60},{\"start\":120,\"end\":180}]");
    }

    @Test
    @DisplayName("Should create a new reencode edit request")
    void createReencodeEditRequestSuccess() {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(mockRecording.getId());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setForceReencode(true);

        EditRequest response = underTest.prepareEditRequestToCreateOrUpdate(dto, mockRecording, editRequest);

        EditInstructions editInstructions = EditInstructions.tryFromJson(response.getEditInstruction());
        assertThat(editInstructions).isNotNull();
        assertThat(editInstructions.getRequestedInstructions()).isEmpty();
        assertThat(editInstructions.getFfmpegInstructions()).isEmpty();
        assertThat(editInstructions.isForceReencode()).isTrue();
        assertThat(editInstructions.shouldSendNotifications()).isTrue();
    }


    private String generateEditInstructionsJson(List<FfmpegEditInstructionDTO> ffmpegEditInstructions)
        throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        EditInstructions instructions = new EditInstructions(List.of(), ffmpegEditInstructions);

        return objectMapper.writeValueAsString(instructions);
    }

    private List<FfmpegEditInstructionDTO> setRandomEditInstructions() throws JsonProcessingException {
        Random random = new Random();
        List<@NotNull FfmpegEditInstructionDTO> instructions = List.of(
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

    private static FfmpegEditInstructionDTO createSegment(long start, long end) {
        return new FfmpegEditInstructionDTO(start, end);
    }

    private static EditCutInstructionDTO createCut(long start, long end, String reason) {
        return new EditCutInstructionDTO(start, end, reason);
    }
}
