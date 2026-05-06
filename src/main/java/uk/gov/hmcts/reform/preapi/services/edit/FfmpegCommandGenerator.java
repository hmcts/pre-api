package uk.gov.hmcts.reform.preapi.services.edit;

import org.apache.commons.exec.CommandLine;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;

public class FfmpegCommandGenerator {

    protected static CommandLine generateSingleEditCommand(final FfmpegEditInstructionDTO instruction,
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

    protected static CommandLine generateReencodeCommand(final String inputFileName, final String outputFileName) {
        return new CommandLine("ffmpeg")
            .addArgument("-fflags").addArgument("+genpts")
            .addArgument("-i").addArgument(inputFileName)
            .addArgument("-c:v").addArgument("copy")
            .addArgument("-af").addArgument("aresample=async=1")
            .addArgument("-c:a").addArgument("aac")
            .addArgument("-b:a").addArgument("128k")
            .addArgument("-movflags").addArgument("+faststart")
            .addArgument(outputFileName);
    }

    protected static CommandLine generateConcatCommand(final String concatListFileName, final String outputFileName) {
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

    protected static CommandLine generateGetDurationCommand(final String fileName) {
        return new CommandLine("ffprobe")
            .addArgument("-v")
            .addArgument("error")
            .addArgument("-show_entries")
            .addArgument("format=duration")
            .addArgument("-of")
            .addArgument("default=noprint_wrappers=1:nokey=1")
            .addArgument(fileName, true);
    }

}
