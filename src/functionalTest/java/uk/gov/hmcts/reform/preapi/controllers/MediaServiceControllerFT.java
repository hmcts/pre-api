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

class MediaServiceControllerFT extends FunctionalTestBase {

    private static final String VOD_ENDPOINT = "/media-service/vod";

    @Test
    @DisplayName("Should return links to play back a vod")
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

    @Test
    @DisplayName("Should return 403 when level 4 user attempts to access a vod")
    void getVodLevel4() {
        Response getVodResponse = doGetRequest(VOD_ENDPOINT + "?recordingId=" + UUID.randomUUID(),
                                               TestingSupportRoles.LEVEL_4);
        assertResponseCode(getVodResponse, 403);
    }

    private ResponseBody<Response> getVodLinks(UUID recordingId) {
        return doGetRequest(VOD_ENDPOINT + "?recordingId=" + recordingId + "&mediaService=MediaKind",
                            TestingSupportRoles.SUPER_USER);
    }
}
