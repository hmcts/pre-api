package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditCutInstructionsDTO;
import uk.gov.hmcts.reform.preapi.dto.edit.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class FfmpegService implements IEditingService {
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final CommandExecutor commandExecutor;

    private static final String CONCAT_FILENAME = "concat-list.txt";

    @Autowired
    public FfmpegService(AzureIngestStorageService azureIngestStorageService,
                         AzureFinalStorageService azureFinalStorageService,
                         CommandExecutor commandExecutor) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.commandExecutor = commandExecutor;
    }

    // Change this to accept a recording with embedded edit request
    @Override
    public void performEdit(UUID newRecordingId, RecordingDTO recording) {
        String inputFileName = recording.getFilename();
        if (inputFileName == null) {
            throw new NotFoundException("No file name provided");
        }

        String outputFileName = newRecordingId + ".mp4";

        final List<String> filesToDelete = new ArrayList<>();
        filesToDelete.add(inputFileName);
        filesToDelete.add(outputFileName);

        final Map<String, CommandLine> commands = generateMultiEditCommands(newRecordingId, outputFileName, recording);

        downloadInputFile(recording.getEditRequest(), inputFileName);
        try {
            runFfmpegCommandsWithFilenames(
                recording.getEditRequest().getId(), newRecordingId, inputFileName, outputFileName,
                commands, filesToDelete
            );
        } finally {
            cleanup(filesToDelete);
        }
    }

    private void runFfmpegCommandsWithFilenames(UUID requestId, UUID newRecordingId, String inputFileName,
                                                String outputFileName, Map<String, CommandLine> commands,
                                                List<String> filesToDelete) {
        // single command
        if (commands.size() == 1) {
            runFfmpegCommand(
                requestId,
                commands.get(outputFileName),
                inputFileName,
                outputFileName,
                filesToDelete
            );
            uploadOutputFile(newRecordingId, outputFileName, filesToDelete);
            cleanup(filesToDelete);
            return;
        }

        final long ffmpegStart = System.currentTimeMillis();

        // multi-command
        commands.forEach((segmentFileName, command) -> {
            filesToDelete.add(segmentFileName);
            runFfmpegCommand(requestId, command, inputFileName, segmentFileName, filesToDelete);
        });

        // concat segments
        generateConcatListFile(commands.keySet());
        filesToDelete.add(CONCAT_FILENAME);
        final CommandLine concatCommand = generateConcatCommand(CONCAT_FILENAME, outputFileName);
        runFfmpegConcatCommand(requestId, concatCommand, outputFileName);

        final long ffmpegEnd = System.currentTimeMillis();
        log.info("All ffmpeg commands completed in {} ms", ffmpegEnd - ffmpegStart);

        uploadOutputFile(newRecordingId, outputFileName, filesToDelete);
    }

    private void runFfmpegCommand(final UUID requestId,
                                  final CommandLine command,
                                  final String inputFileName,
                                  final String outputFileName,
                                  final List<String> filesToDelete) {
        final long ffmpegStart = System.currentTimeMillis();
        if (!commandExecutor.execute(command)) {
            cleanup(filesToDelete);
            throw new UnknownServerException("Error occurred when attempting to process edit request: "
                                                 + requestId);
        }
        log.info("Successfully applied edit instructions to: {} and created output: {}", inputFileName, outputFileName);
        final long ffmpegEnd = System.currentTimeMillis();
        log.info("Ffmpeg completed in {} ms", ffmpegEnd - ffmpegStart);
    }

    private void runFfmpegConcatCommand(final UUID requestId, final CommandLine command, final String outputFileName) {
        final long ffmpegStart = System.currentTimeMillis();
        if (!commandExecutor.execute(command)) {
            throw new UnknownServerException("Error occurred when attempting to process edit request: "
                                                 + requestId);
        }
        log.info("Successfully applied ffmpeg concat command and created output: {}", outputFileName);
        final long ffmpegEnd = System.currentTimeMillis();
        log.info("Ffmpeg concat completed in {} ms", ffmpegEnd - ffmpegStart);
    }

    public void cleanup(final List<String> files) {
        files.forEach(this::deleteFile);
    }

    public Duration getDurationFromMp4(final String containerName, final String fileName) {
        if (!azureFinalStorageService.downloadBlob(containerName, fileName, fileName)) {
            log.error("Failed to download mp4 file from container {}", containerName);
            return null;
        }

        final CommandLine command = new CommandLine("ffprobe")
            .addArgument("-v")
            .addArgument("error")
            .addArgument("-show_entries")
            .addArgument("format=duration")
            .addArgument("-of")
            .addArgument("default=noprint_wrappers=1:nokey=1")
            .addArgument(fileName, true);

        try {
            final String strDurationInSeconds = commandExecutor.executeAndGetOutput(command);
            deleteFile(fileName);
            if (strDurationInSeconds != null) {
                final double seconds = Double.parseDouble(strDurationInSeconds.trim());
                return Duration.ofMillis((long) (seconds * 1000));
            }
        } catch (Exception e) {
            log.error("Failed to get duration from MP4 for recording with error: {}", e.getMessage());
        } finally {
            deleteFile(fileName);
        }
        return null;
    }

    private void deleteFile(String fileName) {
        File file = new File(fileName);
        try {
            if (file.delete()) {
                log.info("Successfully deleted file: {}", fileName);
            } else {
                log.info("Failed to delete file: {}", fileName);
            }
        } catch (Exception e) {
            log.error("Error deleting file: {}", fileName, e);
        }
    }

    private record FfmpegEditInstruction(long start, long end) {
    }

    private List<FfmpegEditInstruction> invertInstructions(final List<EditCutInstructionsDTO> instructions,
                                                           final long recordingDuration) {
        long currentTime = 0L;
        List<FfmpegEditInstruction> invertedInstructions = new ArrayList<>();

        for (EditCutInstructionsDTO instruction : instructions) {
            if (currentTime < instruction.getStart()) {
                invertedInstructions.add(new FfmpegEditInstruction(currentTime, instruction.getStart()));
            }
            currentTime = Math.max(currentTime, instruction.getEnd());
        }

        if (currentTime != recordingDuration) {
            invertedInstructions.add(new FfmpegEditInstruction(currentTime, recordingDuration));
        }

        return invertedInstructions;
    }

    private CommandLine generateSingleEditCommand(final FfmpegEditInstruction instruction,
                                                  final String inputFileName,
                                                  final String outputFileName) {
        return new CommandLine("ffmpeg")
            .addArgument("-y")
            .addArgument("-ss").addArgument(String.valueOf(instruction.start()))
            .addArgument("-to").addArgument(String.valueOf(instruction.end()))
            .addArgument("-i").addArgument(inputFileName)
            .addArgument("-c").addArgument("copy")
            .addArgument("-avoid_negative_ts").addArgument("1")
            .addArgument(outputFileName);
    }

    protected CommandLine generateConcatCommand(final String concatListFileName, final String outputFileName) {
        return new CommandLine("ffmpeg")
            .addArgument("-f")
            .addArgument("concat")
            .addArgument("-safe")
            .addArgument("0")
            .addArgument("-i")
            .addArgument(concatListFileName)
            .addArgument("-c")
            .addArgument("copy")
            .addArgument(outputFileName);
    }

    private LinkedHashMap<String, CommandLine> generateMultiEditCommands(final UUID newRecordingId,
                                                                         final String outputFileName,
                                                                         final RecordingDTO recording) {
        List<EditCutInstructionsDTO> rawEditInstructions = recording.getEditRequest().getEditInstructions();

        long recordingDuration = recording.getDuration().toSeconds();
        List<FfmpegEditInstruction> ffmpegEditInstructions = invertInstructions(rawEditInstructions, recordingDuration);

        if (ffmpegEditInstructions.size() == 1) {
            FfmpegEditInstruction instruction = ffmpegEditInstructions.getFirst();
            return new LinkedHashMap<>(Map.of(
                outputFileName,
                generateSingleEditCommand(instruction, recording.getFilename(), outputFileName)
            ));
        }

        return IntStream.range(0, ffmpegEditInstructions.size())
            .mapToObj(i -> {
                String segmentFileName = String.format("%d_segment.mp4", i);
                return Map.entry(
                    segmentFileName,
                    generateSingleEditCommand(ffmpegEditInstructions.get(i), recording.getFilename(), segmentFileName)
                );
            })
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1,
                LinkedHashMap::new
            ));
    }

    protected void generateConcatListFile(final Set<String> segmentFiles) {
        try (BufferedWriter writer = Files.newBufferedWriter(
            Paths.get(FfmpegService.CONCAT_FILENAME),
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING
        )) {
            for (String file : segmentFiles) {
                writer.write("file '" + file + "'");
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UnknownServerException("Error occurred when attempting to generate concat list file", e);
        }
    }

    private void downloadInputFile(final EditRequestDTO request,
                                   final String inputFileName) {
        final long downloadStart = System.currentTimeMillis();
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecordingId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }
        final long downloadEnd = System.currentTimeMillis();
        log.info("Download completed in {} ms", downloadEnd - downloadStart);

    }

    private void uploadOutputFile(final UUID newRecordingId,
                                  final String outputFileName,
                                  final List<String> filesToDelete) {
        final long uploadStart = System.currentTimeMillis();
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanup(filesToDelete);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }
        final long uploadEnd = System.currentTimeMillis();
        log.info("Upload completed in {} ms", uploadEnd - uploadStart);
    }
}
