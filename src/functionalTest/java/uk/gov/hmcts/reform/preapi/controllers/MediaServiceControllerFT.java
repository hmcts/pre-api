package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import io.restassured.response.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MediaServiceControllerFT extends FunctionalTestBase {

    @DisplayName("Should return links to play back a vod on demand")
    @Test
    void vodLinks() throws JsonProcessingException {
        var captureSession = createCaptureSession();
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);
        assertCaptureSessionExists(captureSession.getId(), true);

        // create recording
        var recording = createRecording(captureSession.getId());
        var putRecording1 = putRecording(recording);
        assertResponseCode(putRecording1, 201);
        var response = assertRecordingExists(recording.getId(), true);
        assertThat(response.body().jsonPath().getString("filename")).isEqualTo("example.file");

        // check for presence of vod links
        var links = getVodLinks(recording.getId());
        // Asset won't be found as we just made the recording in the db not a real recording in MediaKind
        assertThat(links.peek().jsonPath().getString("message"))
                .isEqualTo("Asset with name: " + recording.getId().toString().replace("-", "") + "_output not found");
        assertResponseCode(links.peek(), 404);
    }

    private ResponseBody<Response> getVodLinks(UUID recordingId) {
        return doGetRequest("/media-service/vod?recordingId=" + recordingId + "&mediaService=MediaKind",
                            TestingSupportRoles.SUPER_USER);
    }
}
