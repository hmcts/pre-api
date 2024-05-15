package uk.gov.hmcts.reform.preapi.media;

import feign.Feign;
import feign.FeignException;
import feign.gson.GsonDecoder;
import feign.gson.GsonEncoder;
import feign.okhttp.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.exception.MediaKindException;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class MediaKind implements IMediaService {
    private final String mkToken;

    protected MediaKindClient client;

    public MediaKind(
        @Value("${mediakind.api:}") String mkApi,
        @Value("${mediakind.token:}") String token
    ) {
        client = Feign.builder()
            .client(new OkHttpClient())
            .encoder(new GsonEncoder())
            .decoder(new GsonDecoder())
            .target(MediaKindClient.class, mkApi);
        mkToken = token;
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException();
    }

    @Override
    public AssetDTO getAsset(String assetId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<AssetDTO> getAssets() {
        try {
            var res = client.getAssets(mkToken).getValue();
            return res.stream()
                .map(AssetDTO::new)
                .collect(Collectors.toList());
        } catch (FeignException e) {
            throw new MediaKindException();
        }
    }

    @Override
    public String startLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String playLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String stopLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLiveEvent(String liveEventId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getLiveEvents() {
        throw new UnsupportedOperationException();
    }
}
