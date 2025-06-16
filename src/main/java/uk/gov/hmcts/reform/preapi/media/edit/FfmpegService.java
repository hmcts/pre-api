package uk.gov.hmcts.reform.preapi.media.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureIngestStorageService;

import java.io.File;
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

    @Override
    public void performEdit(UUID newRecordingId, EditRequest request) {
        String inputFileName = request.getSourceRecording().getFilename();
        if (inputFileName == null) {
            throw new NotFoundException("No file name provided");
        }

        String outputFileName = newRecordingId + ".mp4";
        CommandLine command = generateCommand(request, inputFileName, outputFileName);

        // download from final storage
        if (!azureFinalStorageService
            .downloadBlob(request.getSourceRecording().getId().toString(), inputFileName, inputFileName)) {
            throw new UnknownServerException("Error occurred when attempting to download file: " + inputFileName);
        }

        // apply ffmpeg
        if (!commandExecutor.execute(command)) {
            cleanup(inputFileName, outputFileName);
            throw new UnknownServerException("Error occurred when attempting to process edit request: "
                                                 + request.getId());
        }
        log.info("Successfully applied edit instructions to: {} and created output: {}", inputFileName, outputFileName);

        // upload to ingest storage
        if (!azureIngestStorageService.uploadBlob(outputFileName, newRecordingId + "-input", outputFileName)) {
            cleanup(inputFileName, outputFileName);
            throw new UnknownServerException("Error occurred when attempting to upload file");
        }

        cleanup(inputFileName, outputFileName);
    }

    @Override
    public void cleanup(String inputFile, String outputFile) {
        deleteFile(inputFile);
        deleteFile(outputFile);
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

    protected CommandLine generateCommand(EditRequest editRequest, String inputFileName, String outputFileName) {
        EditInstructions instructions = fromJson(editRequest.getEditInstruction());

        if (instructions.getFfmpegInstructions() == null
            || instructions.getFfmpegInstructions().isEmpty()) {
            throw new UnknownServerException("Malformed edit instructions");
        }

        CommandLine command = new CommandLine("ffmpeg");
        command.addArgument("-y")
            .addArgument("-i").addArgument(inputFileName);

        StringBuilder filterComplex = new StringBuilder();
        StringBuilder concatCommand = new StringBuilder(instructions.getFfmpegInstructions().size() * 8 + 40);
        for (int i = 0; i < instructions.getFfmpegInstructions().size(); i++) {
            int segment = i + 1;
            FfmpegEditInstructionDTO instruction =  instructions.getFfmpegInstructions().get(i);


            filterComplex
                // video trim
                .append(String.format("[0:v]trim=start=%d:end=%d,setpts=PTS-STARTPTS[v%d];",
                                               instruction.getStart(), instruction.getEnd(), segment))
                // audio trim
                .append(String.format("[0:a]atrim=start=%d:end=%d,asetpts=PTS-STARTPTS[a%d];",
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
            throw new UnknownServerException("Unable to read edit instructions", e);
        }
    }
}
