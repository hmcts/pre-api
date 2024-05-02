package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.exception.ManagementException;
import com.azure.core.management.profile.AzureProfile;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.resourcemanager.mediaservices.MediaServicesManager;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;

import java.util.List;

@Component
public class AzureMediaService implements IMediaService {
    private final String resourceGroup;
    private final String accountName;
    protected AzureMediaServices client;

    @Autowired
    public AzureMediaService(
        @Value("${azure.subscription-id}") String subscriptionId,
        @Value("${azure.tenant-id}") String tenantId,
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        @Value("${azure.client-id}") String clientId,
        @Value("${azure.client-secret}") String clientSecret
    ) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        var profile = new AzureProfile(tenantId, subscriptionId, AzureEnvironment.AZURE);
        var credentials = new ClientSecretCredentialBuilder()
            .clientId(clientId)
            .clientSecret(clientSecret)
            .tenantId(tenantId)
            .authorityHost(profile.getEnvironment().getActiveDirectoryEndpoint())
            .build();
        client = MediaServicesManager.authenticate(credentials, profile).serviceClient();
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
    public AssetDTO getAsset(String assetName) {
        if (client == null) {
            throw new NotFoundException("Azure service client not found");
        }
        // assetName is an id without the '-'
        try {
            return new AssetDTO(client.getAssets().get(resourceGroup, accountName, assetName));
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Asset with name: " + assetName);
            }
            throw e;
        }
    }

    @Override
    public List<AssetDTO> getAssets() {
        if (client == null) {
            throw new NotFoundException("Azure service client not found");
        }
        return client
            .getAssets()
            .list(resourceGroup, accountName)
            .stream()
            .map(AssetDTO::new)
            .toList();
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
