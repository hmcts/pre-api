package uk.gov.hmcts.reform.preapi.media;

import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;

import java.util.List;

public interface IMediaService {
    String playAsset(String assetId);

    String importAsset(String assetPath);

    AssetDTO getAsset(String assetId);

    List<AssetInner> getAssets();

    String startLiveEvent(String liveEventId);

    String playLiveEvent(String liveEventId);

    String stopLiveEvent(String liveEventId);

    String getLiveEvent(String liveEventId);

    String getLiveEvents();
}
