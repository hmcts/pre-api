package uk.gov.hmcts.reform.preapi.media;

public class AzureMediaService implements IMediaService {

    public AzureMediaService() {
        // constructor implementation
    }

    @Override
    public String playAsset(String assetId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String importAsset(String assetPath) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAsset(String assetId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getAssets() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
