package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class EditControllerFT extends FunctionalTestBase {
    private static final String VALID_EDIT_CSV = "src/functionalTest/resources/test/edit/edit_from_csv.csv";
    private static final String EDIT_ENDPOINT = "/edits";

    private static final Map<String, String> MULTIPART_HEADERS =
        Map.of("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE);

    @Test
    @DisplayName("Should create an edit request from a csv")
    void editFromCsvSuccess() throws JsonProcessingException {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId(), true);

        var postResponse = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.SUPER_USER
        ).as(EditRequestDTO.class);

        assertThat(postResponse.getId()).isNotNull();
        assertThat(postResponse.getSourceRecording().getId()).isEqualTo(recordingDetails.recordingId());
        assertThat(postResponse.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        assertThat(postResponse.getEditInstruction()).isNotNull();
        var instructions = OBJECT_MAPPER.readValue(postResponse.getEditInstruction(),
                                                   new TypeReference<EditInstructions>() {});
        assertThat(instructions).isNotNull();

        var requestedInstructions = instructions.getRequestedInstructions();
        assertThat(requestedInstructions).isNotEmpty();
        assertThat(requestedInstructions.size()).isEqualTo(2);

        assertThat(requestedInstructions.getFirst().getStartOfCut()).isEqualTo("00:00:00");
        assertThat(requestedInstructions.getFirst().getEndOfCut()).isEqualTo("00:01:00");
        assertThat(requestedInstructions.getFirst().getReason()).isEqualTo("I don’t want this");
        assertThat(requestedInstructions.getFirst().getStart()).isEqualTo(0);
        assertThat(requestedInstructions.getFirst().getEnd()).isEqualTo(60);

        assertThat(requestedInstructions.getLast().getStartOfCut()).isEqualTo("00:01:01");
        assertThat(requestedInstructions.getLast().getEndOfCut()).isEqualTo("00:02:00");
        assertThat(requestedInstructions.getLast().getReason()).isEmpty();
        assertThat(requestedInstructions.getLast().getStart()).isEqualTo(61);
        assertThat(requestedInstructions.getLast().getEnd()).isEqualTo(120);

        var ffmpegInstructions = instructions.getFfmpegInstructions();
        assertThat(ffmpegInstructions).isNotEmpty();
        assertThat(ffmpegInstructions.size()).isEqualTo(2);

        assertThat(ffmpegInstructions.getFirst().getStart()).isEqualTo(60);
        assertThat(ffmpegInstructions.getFirst().getEnd()).isEqualTo(61);

        assertThat(ffmpegInstructions.getLast().getStart()).isEqualTo(120);
        assertThat(ffmpegInstructions.getLast().getEnd()).isEqualTo(180);
    }

    @Test
    @DisplayName("Should return forbidden for users attempting to use from csv endpoint with incorrect role")
    void editRequestFromCsvForbidden() {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId(), true);

        // Level 1
        var responseLevel1 = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(responseLevel1, 403);

        // Level 2
        var responseLevel2 = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.LEVEL_2
        );
        assertResponseCode(responseLevel2, 403);

        // Level 3
        var responseLevel3 = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.LEVEL_3
        );
        assertResponseCode(responseLevel3, 403);

        // Level 4
        var responseLevel4 = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.LEVEL_4
        );
        assertResponseCode(responseLevel4, 403);

        // No X-User-Id
        var responseNoRole = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            null
        );
        assertResponseCode(responseNoRole, 401);
    }
}
