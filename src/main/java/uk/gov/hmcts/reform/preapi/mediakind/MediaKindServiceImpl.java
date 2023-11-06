package uk.gov.hmcts.reform.preapi.mediakind;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.hmcts.reform.preapi.controllers.response.StreamingLinkResponse;

import java.io.IOException;

@Service
public class MediaKindServiceImpl implements MediaKindService {

    // TODO API Key goes here (get from env vars)
    private static final String API_KEY = "API-KEY";
    // TODO Subscription Name goes here (get from env vars)
    private static final String SUBSCRIPTION_NAME = "SUBSCRIPTION-NAME";
    // TODO Streaming Endpoint goes here (get from env vars)
    private static final String STREAMING_HOST = "STREAMING-ENDPOINT";

    // Request URLs
    private static final String REQUEST_POST_LIST_STREAMING_LOCATORS = "https://api.io.mediakind.com/api/ams/" + SUBSCRIPTION_NAME + "/assets/%s/listStreamingLocators";
    private static final String REQUEST_POST_LIST_URL_PATHS_FOR_LOCATOR = "https://api.io.mediakind.com/api/ams/" + SUBSCRIPTION_NAME + "/streamingLocators/%s/listPaths";

    // Request Client
    private static final OkHttpClient httpClient = new OkHttpClient();

    // Empty Request Body
    private static final RequestBody EMPTY_REQUEST_BODY = new RequestBody() {
        @Nullable
        @Override
        public MediaType contentType() {
            return null;
        }

        @Override
        public void writeTo(@NotNull BufferedSink bufferedSink) throws IOException {

        }
    };

    private static ResponseBody executeRequest(Request req)
        throws ResponseStatusException {
        // TODO Error handling
        Response res;
        try {
            res = httpClient.newCall(req).execute();
            if (!res.isSuccessful()) {
                throw new ResponseStatusException(HttpStatus.valueOf(res.code()), "mk request failed");
            }
            ResponseBody responseBody = res.body();

            if (responseBody == null) {
                throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "mk request failed");
            }

            return res.body();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "mk request failed");
        }
    }

    @Override
    public String getStreamingLocatorName(String assetName)
        throws ResponseStatusException {
        // TODO proper error handling
        String requestUrl = String.format(REQUEST_POST_LIST_STREAMING_LOCATORS, assetName);

        Request request = new Request.Builder()
            .url(requestUrl)
            .post(EMPTY_REQUEST_BODY)
            .addHeader("accept", "application/json")
            .addHeader("x-mkio-token", API_KEY)
            .build();

        ResponseBody res = executeRequest(request);

        try {
            JsonObject json = JsonParser
                .parseString(res.string())
                .getAsJsonObject();
            return json
                .getAsJsonArray("streamingLocators")
                .get(0)
                .getAsJsonObject()
                .get("name")
                .getAsString();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "mk request failed");
        }
    }

    @Override
    public StreamingLinkResponse getStreamingPathsForStreamingLocator(String locatorName)
        throws ResponseStatusException {
        // TODO proper error handling

        String requestUrl = String.format(REQUEST_POST_LIST_URL_PATHS_FOR_LOCATOR, locatorName);

        Request request = new Request.Builder()
            .url(requestUrl)
            .post(EMPTY_REQUEST_BODY)
            .addHeader("accept", "application/json")
            .addHeader("x-mkio-token", API_KEY)
            .build();

        ResponseBody res = executeRequest(request);

        try {
            JsonObject json = JsonParser
                .parseString(res.string())
                .getAsJsonObject();
            StreamingLinkResponse data = new StreamingLinkResponse();
            json
                .getAsJsonArray("streamingPaths")
                .forEach((JsonElement path) -> {
                    String protocol = path
                        .getAsJsonObject()
                        .get("streamingProtocol")
                        .getAsString();
                    String streamingUrl = STREAMING_HOST + path
                        .getAsJsonObject()
                        .getAsJsonArray("paths")
                        .get(0)
                        .getAsString();

                    if (protocol.equals("Hls")) {
                        data.setHls(streamingUrl);
                    } else if (protocol.equals("Dash")) {
                        data.setDash(streamingUrl);
                    }
                });
            return data;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.REQUEST_TIMEOUT, "mk request failed");
        }
    }
}
