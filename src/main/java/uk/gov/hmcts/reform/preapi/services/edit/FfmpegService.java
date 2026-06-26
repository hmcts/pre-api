package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static uk.gov.hmcts.reform.preapi.services.edit.EditRequestValidator.SINGLETON_LIST_SIZE;
import static uk.gov.hmcts.reform.preapi.services.edit.FfmpegCommandGenerator.generateConcatCommand;
import static uk.gov.hmcts.reform.preapi.services.edit.FfmpegCommandGenerator.generateGetDurationCommand;
import static uk.gov.hmcts.reform.preapi.services.edit.FfmpegCommandGenerator.generateReencodeCommand;
import static uk.gov.hmcts.reform.preapi.services.edit.FfmpegCommandGenerator.generateSingleEditCommand;
import static uk.gov.hmcts.reform.preapi.utils.JsonUtils.toJson;

@Slf4j
@Service
public class FfmpegService implements IEditingService {
    private final CommandExecutor commandExecutor;
    private final IFileUploader editedFileUploader;

    private static final String CONCAT_FILENAME = "concat-list.txt";

    @Autowired
    public FfmpegService(final CommandExecutor commandExecutor,
                         final IFileUploader editedFileUploader) {
        this.commandExecutor = commandExecutor;
        this.editedFileUploader = editedFileUploader;
    }

    @Override
    public void performEdit(UUID newRecordingId, EditRequest request) {

        String inputFileName = EditRequestValidator.checkEditRequestHasValidFileName(request);

        String outputFileName = newRecordingId + ".mp4";
        performCommandsForEdit(request, newRecordingId, inputFileName, outputFileName);
    }

    private void performCommandsForEdit(final EditRequest request,
                                        final UUID newRecordingId,
                                        final String inputFileName,
                                        final String outputFileName) {
        final List<String> filesToDelete = new ArrayList<>();
        filesToDelete.add(inputFileName);
        filesToDelete.add(outputFileName);
        final Map<String, CommandLine> commands = generateMultiEditCommands(request, inputFileName, outputFileName);

        editedFileUploader.downloadInputFile(request, inputFileName);
        try {
            // single command
            if (commands.size() == SINGLETON_LIST_SIZE) {
                runFfmpegCommand(
                        request.getId(),
                        commands.get(outputFileName),
                        inputFileName,
                        outputFileName,
                        filesToDelete
                );
                editedFileUploader.uploadOutputFile(newRecordingId, outputFileName, filesToDelete);
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
            log.info("All ffmpeg commands completed in {} ms", ffmpegEnd - ffmpegStart);

            editedFileUploader.uploadOutputFile(newRecordingId, outputFileName, filesToDelete);
        } finally {
            editedFileUploader.cleanupLocalFiles(filesToDelete);
        }
    }

    private void runFfmpegCommand(final UUID requestId,
                                  final CommandLine command,
                                  final String inputFileName,
                                  final String outputFileName,
                                  final List<String> filesToDelete) {
        final long ffmpegStart = System.currentTimeMillis();
        if (!commandExecutor.execute(command)) {
            editedFileUploader.cleanupLocalFiles(filesToDelete);
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

    public Duration getDurationFromMp4(final String containerName, final String fileName) {
        if (!editedFileUploader.downloadBlob(containerName, fileName)) {
            return null;
        }

        try {
            CommandLine durationCommand = generateGetDurationCommand(fileName);
            final String strDurationInSeconds = commandExecutor.executeAndGetOutput(durationCommand);
            editedFileUploader.cleanupLocalFiles(List.of(fileName));
            if (strDurationInSeconds != null) {
                final double seconds = Double.parseDouble(strDurationInSeconds.trim());
                return Duration.ofMillis((long) (seconds * 1000));
            }
        } catch (Exception e) {
            log.error("Failed to get duration from MP4 for recording with error: {}", e.getMessage());
        } finally {
            editedFileUploader.cleanupLocalFiles(List.of(fileName));
        }
        return null;
    }

    // Should be private but leaving as protected for now to avoid re-writing tests
    protected Map<String, CommandLine> generateMultiEditCommands(final EditRequest editRequest,
                                                                           final String inputFileName,
                                                                           final String outputFileName) {

        EditInstructions instructions = EditInstructions.fromJson(editRequest.getEditInstruction());

        EditRequestValidator.checkForNonEmptyInstructionsOrForceReencode(instructions);

        if (instructions.isForceReencode()) {
            return new LinkedHashMap<>(Map.of(
                    outputFileName,
                    generateReencodeCommand(inputFileName, outputFileName)
            ));
        }

        if (instructions.getFfmpegInstructions().size() == SINGLETON_LIST_SIZE) {
            FfmpegEditInstructionDTO instruction = instructions.getFfmpegInstructions().getFirst();
            return new LinkedHashMap<>(Map.of(
                    outputFileName,
                   generateSingleEditCommand(instruction, inputFileName, outputFileName)));
        }

        return IntStream.range(0, instructions.getFfmpegInstructions().size())
                .mapToObj(i -> {
                    FfmpegEditInstructionDTO instruction = instructions.getFfmpegInstructions().get(i);
                    String segmentFileName = format("%d_segment.mp4", i);

                    return Map.entry(segmentFileName,
                            generateSingleEditCommand(instruction, inputFileName, segmentFileName));
                })
                .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }

    protected void generateConcatListFile(final Set<String> segmentFiles, final String outputPath) {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputPath),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            for (String file : segmentFiles) {
                writer.write("file '" + file + "'");
                writer.newLine();
            }
        } catch (IOException e) {
            throw new UnknownServerException("Error occurred when attempting to generate concat list file", e);
        }
    }

    @Override
    public @NotNull EditRequest prepareEditRequestToCreateOrUpdate(final CreateEditRequestDTO dto,
                                                                   final Recording sourceRecording,
                                                                   final EditRequest request) {
        if (dto.isForceReencode()) {
            String newEditInstruction = toJson(new EditInstructions(
                List.of(), List.of(), true,
                shouldSendNotifications(dto)));
            request.updateEditRequestFromDto(dto, sourceRecording, newEditInstruction);
            return request;
        }

        boolean isOriginalRecordingEdit = sourceRecording.getParentRecording() == null;

        boolean sourceInstructionsAreNotEmpty = !isOriginalRecordingEdit
                && sourceRecording.getEditInstruction() != null
                && !sourceRecording.getEditInstruction().isEmpty();

        EditInstructions prevInstructions = null;
        if (sourceInstructionsAreNotEmpty) {
            prevInstructions = EditInstructions.tryFromJson(sourceRecording.getEditInstruction());
        }
        boolean prevInstructionsAreNotEmpty = prevInstructions != null
                && prevInstructions.getFfmpegInstructions() != null
                && !prevInstructions.getFfmpegInstructions().isEmpty()
                && prevInstructions.getRequestedInstructions() != null
                && !prevInstructions.getRequestedInstructions().isEmpty();

        boolean isInstructionCombination = sourceInstructionsAreNotEmpty && prevInstructionsAreNotEmpty;

        Recording recordingForUpdatedEdit = !isInstructionCombination
            ? sourceRecording
            : sourceRecording.getParentRecording();

        List<EditCutInstructionDTO> requestedEdits = isInstructionCombination
                ? combineCutsOnOriginalTimeline(prevInstructions, dto.getEditInstructions())
                : dto.getEditInstructions();

        List<FfmpegEditInstructionDTO> editInstructions = invertInstructions(
                requestedEdits,
                isInstructionCombination ? request.getSourceRecording() : sourceRecording
        );

        String newEditInstruction = toJson(new EditInstructions(
            requestedEdits, editInstructions, false,
            shouldSendNotifications(dto)
        ));

        request.updateEditRequestFromDto(dto, recordingForUpdatedEdit, newEditInstruction);

        return request;
    }

    private boolean shouldSendNotifications(CreateEditRequestDTO dto) {
        return !Boolean.FALSE.equals(dto.getSendNotifications());
    }

    // Should be private but leaving as protected to avoid rewriting tests
    protected List<FfmpegEditInstructionDTO> invertInstructions(final List<EditCutInstructionDTO> instructions,
                                                                final Recording recording) {
        long recordingDuration = recording.getDuration().toSeconds();
        EditRequestValidator.checkThatEditsDoNotCutAnEntireRecording(instructions, recordingDuration);

        instructions.sort(Comparator.comparing(EditCutInstructionDTO::getStart)
                .thenComparing(EditCutInstructionDTO::getEnd));

        EditRequestValidator.checkThatEditsDoNotOverlap(instructions);

        long currentTime = 0L;
        List<FfmpegEditInstructionDTO> invertedInstructions = new ArrayList<>();

        // invert
        for (EditCutInstructionDTO instruction : instructions) {
            EditRequestValidator.validateEditInstruction(instruction, recordingDuration);
            if (currentTime < instruction.getStart()) {
                invertedInstructions.add(new FfmpegEditInstructionDTO(currentTime, instruction.getStart()));
            }
            currentTime = Math.max(currentTime, instruction.getEnd());
        }
        if (currentTime != recordingDuration) {
            invertedInstructions.add(new FfmpegEditInstructionDTO(currentTime, recordingDuration));
        }

        return invertedInstructions;
    }

    // Should be private but leaving as protected to avoid rewriting tests
    protected List<EditCutInstructionDTO> combineCutsOnOriginalTimeline(
            final EditInstructions original,
            final List<EditCutInstructionDTO> newInstructions
    ) {
        final List<FfmpegEditInstructionDTO> keptSegments = original.getFfmpegInstructions();

        final List<FfmpegEditInstructionDTO> editedTimelineMapping = new ArrayList<>();
        long cursor = 0;
        for (FfmpegEditInstructionDTO segment : keptSegments) {
            long duration = segment.getEnd() - segment.getStart();
            editedTimelineMapping.add(new FfmpegEditInstructionDTO(cursor, cursor + duration));
            cursor += duration;
        }

        final List<EditCutInstructionDTO> mappedCuts = new ArrayList<>();
        for (EditCutInstructionDTO newCut : newInstructions) {
            for (int i = 0; i < keptSegments.size(); i++) {
                final FfmpegEditInstructionDTO originalSegment = keptSegments.get(i);
                final FfmpegEditInstructionDTO editedSegment = editedTimelineMapping.get(i);

                long start = editedSegment.getStart();
                long end = editedSegment.getEnd();

                if (newCut.getEnd() <= start || newCut.getStart() >= end) {
                    continue;
                }

                long overlapStart = Math.max(start, newCut.getStart());
                long overlapEnd = Math.min(end, newCut.getEnd());

                long offsetInSegment = overlapStart - start;
                long cutLength = overlapEnd - overlapStart;

                long originalMappedStart = originalSegment.getStart() + offsetInSegment;
                long originalMappedEnd = originalMappedStart + cutLength;

                mappedCuts.add(new EditCutInstructionDTO(
                        originalMappedStart,
                        originalMappedEnd,
                        newCut.getReason()
                ));
            }
        }

        mappedCuts.addAll(original.getRequestedInstructions());
        mappedCuts.sort(Comparator.comparing(EditCutInstructionDTO::getStart));

        return mergeOverlappingCuts(mappedCuts)
                .stream()
                .map(cut -> new EditCutInstructionDTO(cut.getStart(), cut.getEnd(), cut.getReason()))
                .collect(Collectors.toList());
    }

    private List<EditCutInstructionDTO> mergeOverlappingCuts(final List<EditCutInstructionDTO> cuts) {
        final List<EditCutInstructionDTO> merged = new ArrayList<>();
        EditCutInstructionDTO current = cuts.getFirst();

        for (int i = 1; i < cuts.size(); i++) {
            final EditCutInstructionDTO next = cuts.get(i);
            if (next.getStart() <= current.getEnd()) {
                current.setEnd(Math.max(current.getEnd(), next.getEnd()));
            } else {
                merged.add(current);
                current = next;
            }
        }
        merged.add(current);
        return merged;
    }

}
