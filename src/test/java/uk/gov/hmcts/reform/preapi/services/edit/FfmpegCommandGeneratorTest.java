package uk.gov.hmcts.reform.preapi.services.edit;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.hmcts.reform.preapi.services.edit.EditRequestTestUtils.createSegment;

@SpringBootTest(classes = FfmpegCommandGenerator.class)
public class FfmpegCommandGeneratorTest {

    @MockitoBean
    private FfmpegCommandGenerator underTest;

    private static final String INPUT_FILENAME = "input.mp4";
    private static final String OUTPUT_FILENAME = "output.mp4";

    @Test
    @DisplayName("Should generate re-encode Ffmpeg command")
    void checkGenerateReencodeCommand(){
        CommandLine command =
            FfmpegCommandGenerator.generateReencodeCommand(INPUT_FILENAME, OUTPUT_FILENAME);

        assertThat(command.getExecutable()).isEqualTo("ffmpeg");
        assertThat(command.getArguments()).containsExactly(
            "-fflags", "+genpts",
            "-i", INPUT_FILENAME,
            "-c:v", "copy",
            "-af", "aresample=async=1",
            "-c:a", "aac",
            "-b:a", "128k",
            "-movflags", "+faststart",
            "output.mp4"
        );
    }

    @Test
    @DisplayName("Should generate a single edit command")
    void shouldGenerateSingleEditCommand(){
        FfmpegEditInstructionDTO instruction = createSegment(20, 30);

        CommandLine command = FfmpegCommandGenerator.generateSingleEditCommand(
            instruction,
            INPUT_FILENAME,
            OUTPUT_FILENAME
        );

        assertThat(command.toString()).contains("-ss", "20", "-to", "30", "-i", INPUT_FILENAME, OUTPUT_FILENAME);
    }

    @Test
    @DisplayName("Should generate concat command")
    void shouldGenerateConcatCommand(){
        CommandLine command = FfmpegCommandGenerator.generateConcatCommand(INPUT_FILENAME, OUTPUT_FILENAME);
        assertThat(command.toString()).contains("-f", "concat", "-safe", "0", "-i", INPUT_FILENAME,
                                                "-c", "copy", OUTPUT_FILENAME);
    }

    @Test
    @DisplayName("Should generate get duration command")
    void shouldGenerateGetDurationCommand(){
        CommandLine command = FfmpegCommandGenerator.generateGetDurationCommand(INPUT_FILENAME);
        assertThat(command.toString()).contains("ffprobe", "-v", "error",
                                                "-show_entries", "format=duration",
                                                "-of", "default=noprint_wrappers=1:nokey=1", INPUT_FILENAME
                                                );
    }
}
