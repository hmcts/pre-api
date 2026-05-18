package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.NotNull;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@UtilityClass
@SuppressWarnings("checkstyle:HideUtilityClassConstructor")
public class EditRequestTestUtils {

    public static void assertEditInstructionsEq(List<FfmpegEditInstructionDTO> expected,
                                                 List<FfmpegEditInstructionDTO> actual) {
        assertThat(actual.size()).isEqualTo(expected.size());

        for (int i = 0; i < expected.size(); i++) {
            assertThat(actual.get(i).getStart()).isEqualTo(expected.get(i).getStart());
            assertThat(actual.get(i).getEnd()).isEqualTo(expected.get(i).getEnd());
        }
    }

    public static FfmpegEditInstructionDTO createSegment(long start, long end) {
        return new FfmpegEditInstructionDTO(start, end);
    }

    public static EditCutInstructionDTO createCut(long start, long end, String reason) {
        return new EditCutInstructionDTO(start, end, reason);
    }

    public static EditInstructions getSampleEditInstructions() {
        List<@NotNull FfmpegEditInstructionDTO> originallyKeptSegments = List.of(createSegment(0, 10),
                                                                                 createSegment(20, 30));
        List<@NotNull EditCutInstructionDTO> originalCutSegments = List.of(createCut(10, 20,
                                                                                     "some reason"));
        return new EditInstructions(originalCutSegments, originallyKeptSegments);
    }

    public static List<EditCutInstructionDTO> getSampleEditCutInstructions() {
        return new ArrayList<>(List.of(
            createCut(10, 20, "new edit instructions")));
    }
}
