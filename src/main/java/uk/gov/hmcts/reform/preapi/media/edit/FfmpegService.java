package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class FfmpegService implements IEditingService {
    private final AzureIngestStorageService azureIngestStorageService;
    private final AzureFinalStorageService azureFinalStorageService;
    private final CommandExecutor commandExecutor;

    @Autowired
    public FfmpegService(AzureIngestStorageService azureIngestStorageService,
                         AzureFinalStorageService azureFinalStorageService,
                         CommandExecutor commandExecutor) {
        this.azureIngestStorageService = azureIngestStorageService;
        this.azureFinalStorageService = azureFinalStorageService;
        this.commandExecutor = commandExecutor;
    }

    public void performEdit(UUID newRecordingId, EditRequest request) {
        var inputFileName = request.getSourceRecording().getFilename();
        var outputFileName = newRecordingId + ".mp4";
        if (inputFileName == null) {
            throw new NotFoundException("No file name provided");
        }
        var command = generateCommand(request, inputFileName, outputFileName);

        final long downloadStart = System.currentTimeMillis();
        // download from final storage
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecording().getId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }
        final long downloadEnd = System.currentTimeMillis();
        log.info("Download completed in {} ms", (downloadEnd - downloadStart));

        // apply ffmpeg
        final long ffmpegStart = System.currentTimeMillis();
        if (!commandExecutor.execute(command)) {
            cleanup(inputFileName, outputFileName);
            throw new UnknownServerException("Error occurred when attempting to process edit request: "
                                                 + request.getId());
        }
        log.info("Successfully applied edit instructions to: {} and created output: {}", inputFileName, outputFileName);
        final long ffmpegEnd = System.currentTimeMillis();
        log.info("Ffmpeg completed in {} ms", (ffmpegEnd - ffmpegStart));

        // upload to ingest storage
        final long uploadStart = System.currentTimeMillis();
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanup(inputFileName, outputFileName);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }
        final long uploadEnd = System.currentTimeMillis();
        log.info("Upload completed in {} ms", (uploadEnd - uploadStart));

        cleanup(inputFileName, outputFileName);
    }

    @Override
    public void cleanup(String inputFile, String outputFile) {
        deleteFile(inputFile);
        deleteFile(outputFile);
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

    private EditInstructions fromJson(String editInstructions) {
        try {
            return new ObjectMapper().readValue(editInstructions, EditInstructions.class);
        } catch (Exception e) {
            log.error("Error reading edit instructions: {} with message: {}", editInstructions, e.getMessage());
            throw new UnknownServerException("Unable to read edit instructions");
        }
    }
}
