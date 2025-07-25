package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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

    private final boolean enableMultiCommand;

    private static final String CONCAT_FILENAME = "concat-list.txt";

    @Autowired
    public FfmpegService(AzureIngestStorageService azureIngestStorageService,
                         AzureFinalStorageService azureFinalStorageService,
                         CommandExecutor commandExecutor,
                         @Value("${feature-flags.ffmpeg-multi-command:false}") Boolean enableMultiCommand) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.commandExecutor = commandExecutor;
        this.enableMultiCommand = enableMultiCommand;
    }

    @Override
    public void performEdit(UUID newRecordingId, EditRequest request) {
        var inputFileName = request.getSourceRecording().getFilename();
        var outputFileName = newRecordingId + ".mp4";
        if (inputFileName == null) {
            throw new NotFoundException("No file name provided");
        }

        if (enableMultiCommand) {
            performEditsAsMultiCommand(request, newRecordingId, inputFileName, outputFileName);
            return;
        }

        var command = generateCommand(request, inputFileName, outputFileName);

        // download from final storage
        downloadInputFile(request, inputFileName);
        List<String> filesToDelete = List.of(inputFileName, outputFileName);

        // apply ffmpeg
        runFfmpegCommand(request.getId(), command, inputFileName, outputFileName, filesToDelete);

        // upload to ingest storage
        uploadOutputFie(newRecordingId, outputFileName, filesToDelete);

        cleanup(filesToDelete);
    }

    private void performEditsAsMultiCommand(final EditRequest request,
                                            final UUID newRecordingId,
                                            final String inputFileName,
                                            final String outputFileName) {
        final List<String> filesToDelete = new ArrayList<>();
        filesToDelete.add(inputFileName);
        filesToDelete.add(outputFileName);
        final Map<String, CommandLine> commands = generateMultiEditCommands(request, inputFileName, outputFileName);

        downloadInputFile(request, inputFileName);
        try {
            // single command
            if (commands.size() == 1) {
                runFfmpegCommand(
                    request.getId(),
                    commands.get(outputFileName),
                    inputFileName,
                    outputFileName,
                    filesToDelete
                );
                uploadOutputFie(newRecordingId, outputFileName, filesToDelete);
                cleanup(filesToDelete);
                return;
            }

            final long ffmpegStart = System.currentTimeMillis();

            // multi-command
            commands.forEach((segmentFileName, command) -> {
                filesToDelete.add(segmentFileName);
                runFfmpegCommand(request.getId(), command, inputFileName, segmentFileName, filesToDelete);
            });

            // concat segments
            generateConcatListFile(commands.keySet(), CONCAT_FILENAME);
            filesToDelete.add(CONCAT_FILENAME);
            final CommandLine concatCommand = generateConcatCommand(CONCAT_FILENAME, outputFileName);
            runFfmpegConcatCommand(request.getId(), concatCommand, outputFileName);

            final long ffmpegEnd = System.currentTimeMillis();
            log.info("All ffmpeg commands completed in {} ms", (ffmpegEnd - ffmpegStart));

            uploadOutputFie(newRecordingId, outputFileName, filesToDelete);
        } finally {
            cleanup(filesToDelete);
        }
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
        log.info("Ffmpeg completed in {} ms", (ffmpegEnd - ffmpegStart));
    }

    private void runFfmpegConcatCommand(final UUID requestId, final CommandLine command, final String outputFileName) {
        final long ffmpegStart = System.currentTimeMillis();
        if (!commandExecutor.execute(command)) {
            throw new UnknownServerException("Error occurred when attempting to process edit request: "
                                                 + requestId);
        }
        log.info("Successfully applied ffmpeg concat command and created output: {}", outputFileName);
        final long ffmpegEnd = System.currentTimeMillis();
        log.info("Ffmpeg concat completed in {} ms", (ffmpegEnd - ffmpegStart));
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
        var file = new File(fileName);
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

    protected CommandLine generateCommand(EditRequest editRequest, String inputFileName, String outputFileName) {
        var instructions = fromJson(editRequest.getEditInstruction());

        if (instructions.getFfmpegInstructions() == null
            || instructions.getFfmpegInstructions().isEmpty()) {
            throw new UnknownServerException("Malformed edit instructions");
        }

        var command = new CommandLine("ffmpeg");
        command.addArgument("-y")
            .addArgument("-i").addArgument(inputFileName);

        var filterComplex = new StringBuilder();
        var concatCommand = new StringBuilder();
        for (int i = 0; i < instructions.getFfmpegInstructions().size(); i++) {
            var segment = i + 1;
            var instruction =  instructions.getFfmpegInstructions().get(i);

            // video trim
            filterComplex.append(String.format("[0:v]trim=start=%d:end=%d,setpts=PTS-STARTPTS[v%d];",
                                               instruction.getStart(), instruction.getEnd(), segment));
            // audio trim
            filterComplex.append(String.format("[0:a]atrim=start=%d:end=%d,asetpts=PTS-STARTPTS[a%d];",
                                               instruction.getStart(), instruction.getEnd(), segment));
            // adding segments to final part of command
            concatCommand.append(String.format("[v%d][a%d]", segment, segment));
        }

        concatCommand.append("concat=n=")
            .append(instructions.getFfmpegInstructions().size())
            .append(":v=1:a=1[outv][outa]");
        filterComplex.append(concatCommand);

        command.addArgument("-filter_complex").addArgument(filterComplex.toString())
            .addArgument("-map").addArgument("[outv]")
            .addArgument("-map").addArgument("[outa]")
            .addArgument("-c:v").addArgument("libx264")
            .addArgument("-c:a").addArgument("aac")
            .addArgument(outputFileName);
        return command;
    }

    protected CommandLine generateSingleEditCommand(final FfmpegEditInstructionDTO instruction,
                                                  final String inputFileName,
                                                  final String outputFileName) {
        return new CommandLine("ffmpeg")
            .addArgument("-y")
            .addArgument("-ss").addArgument(String.valueOf(instruction.getStart()))
            .addArgument("-to").addArgument(String.valueOf(instruction.getEnd()))
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

    protected LinkedHashMap<String, CommandLine> generateMultiEditCommands(final EditRequest editRequest,
                                                               final String inputFileName,
                                                               final String outputFileName) {
        EditInstructions instructions = fromJson(editRequest.getEditInstruction());

        if (instructions.getFfmpegInstructions() == null
            || instructions.getFfmpegInstructions().isEmpty()) {
            throw new UnknownServerException("Malformed edit instructions");
        }

        if (instructions.getFfmpegInstructions().size() == 1) {
            FfmpegEditInstructionDTO instruction = instructions.getFfmpegInstructions().getFirst();
            return new LinkedHashMap<>(Map.of(
                outputFileName,
                generateSingleEditCommand(instruction, inputFileName, outputFileName)));
        }

        return IntStream.range(0, instructions.getFfmpegInstructions().size())
            .mapToObj(i -> {
                FfmpegEditInstructionDTO instruction = instructions.getFfmpegInstructions().get(i);
                String segmentFileName = String.format("%d_segment.mp4", i);
                return Map.entry(segmentFileName,
                                 generateSingleEditCommand(instruction, inputFileName, segmentFileName));
            })
            .collect(Collectors.toMap(Map.Entry::getKey,
                                      Map.Entry::getValue,
                                      (e1, e2) -> e1,
                                      LinkedHashMap::new));
    }

    protected void generateConcatListFile(final Set<String> segmentFiles, final String outputPath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            for (String file : segmentFiles) {
                writer.write("file '" + file + "'");
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UnknownServerException("Error occurred when attempting to generate concat list file");
        }
    }

    private void downloadInputFile(final EditRequest request,
                                   final String inputFileName) throws UnknownServerException {
        final long downloadStart = System.currentTimeMillis();
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecording().getId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }
        final long downloadEnd = System.currentTimeMillis();
        log.info("Download completed in {} ms", (downloadEnd - downloadStart));

    }

    private void uploadOutputFie(final UUID newRecordingId,
                                 final String outputFileName,
                                 final List<String> filesToDelete) {
        final long uploadStart = System.currentTimeMillis();
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanup(filesToDelete);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }
        final long uploadEnd = System.currentTimeMillis();
        log.info("Upload completed in {} ms", (uploadEnd - uploadStart));
    }

    private EditInstructions fromJson(String editInstructions) {
        try {
            return new ObjectMapper().readValue(editInstructions, EditInstructions.class);
        } catch (Exception e) {
            log.error("Error reading edit instructions: {} with message: {}", editInstructions, e.getMessage());
            throw new UnknownServerException("Unable to read edit instructions");
        }
    }
}
