package uk.gov.hmcts.reform.preapi.services.edit;

import lombok.experimental.UtilityClass;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;

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
}
