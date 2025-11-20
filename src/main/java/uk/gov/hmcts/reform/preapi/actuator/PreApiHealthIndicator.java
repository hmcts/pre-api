package uk.gov.hmcts.reform.preapi.actuator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.media.MediaKindAccountsClient;
import uk.gov.hmcts.reform.preapi.media.dto.MkStorageAccounts;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class PreApiHealthIndicator implements HealthIndicator {
    @Value("${media-service}")
    protected String mediaService;

    @Value("${azure.ingestStorage.accountName}")
    protected String ingestStorageAccountName;

    @Value("${azure.finalStorage.accountName}")
    protected String finalStorageAccountName;

    public final MediaKindAccountsClient mediaKindAccountsClient;

    private static final String MEDIA_KIND = "MediaKind";

    @Autowired
    public PreApiHealthIndicator(@Lazy MediaKindAccountsClient mediaKindAccountsClient) {
        this.mediaKindAccountsClient = mediaKindAccountsClient;
    }

    @Override
    public Health health() {
        if (!MEDIA_KIND.equals(mediaService)) {
            return Health.up().build();
        }
        try {
            Map<String, Boolean> details = checkMediaKindConnections();
            return Health.up().withDetail("mediakindConnections", details).build();
        } catch (Exception e) {
            log.error("Encountered an error when attempting to check MK storage account connections: {}",
                      e.getMessage());
        }

        return Health.up().withDetail("mediakindConnections", "unknown error").build();
    }

    private Map<String, Boolean> checkMediaKindConnections() {
        MkStorageAccounts storageAccounts = mediaKindAccountsClient.getStorageAccounts();
        if (storageAccounts.getItems().isEmpty()) {
            log.error("MediaKind does not have any storage account connections");
            return Map.of(
                ingestStorageAccountName, false,
                finalStorageAccountName, false
            );
        }

        List<String> expectedStorageAccounts = List.of(ingestStorageAccountName, finalStorageAccountName);
        return expectedStorageAccounts
            .stream()
            .map(storageAccount ->
                storageAccounts
                    .getItems()
                    .stream()
                    .filter(connection -> connection.getSpec().getName().equals(storageAccount))
                    .findFirst()
                    .map(connection -> {
                        if (connection.getStatus().getActiveCredentialId() == null
                            || !Objects.equals(connection.getStatus().getPrivateLinkServiceConnectionStatus(),
                                               "Running")) {
                            log.error("MediaKind connection {} is not active", storageAccount);
                            return Map.entry(storageAccount, false);
                        } else {
                            log.info("MediaKind connection {} is active", storageAccount);
                            return Map.entry(storageAccount, true);
                        }
                    })
                    .or(
                        () -> {
                            log.error("MediaKind connection {} not found", storageAccount);
                            return Optional.of(Map.entry(storageAccount, false));
                        }))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
