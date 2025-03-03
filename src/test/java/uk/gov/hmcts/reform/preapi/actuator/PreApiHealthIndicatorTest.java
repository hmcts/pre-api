package uk.gov.hmcts.reform.preapi.actuator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.media.MediaKindAccountsClient;
import uk.gov.hmcts.reform.preapi.media.dto.MkStorageAccount;
import uk.gov.hmcts.reform.preapi.media.dto.MkStorageAccounts;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PreApiHealthIndicator.class)
public class PreApiHealthIndicatorTest {

    @MockitoBean
    private MediaKindAccountsClient mediaKindAccountsClient;

    @Autowired
    private PreApiHealthIndicator preApiHealthIndicator;

    @Test
    @DisplayName("Should return health up when using media service other than MK")
    void healthUpNotUsingMediaKind() {
        preApiHealthIndicator.mediaService = "AzureMediaService";

        var res = preApiHealthIndicator.health();

        assertThat(res.getStatus()).isEqualTo(Status.UP);
    }

    @Test
    @DisplayName("Should return health and connection details when connections successful")
    void healthUpAndConnectionDetailsWhenMkSuccess() {
        preApiHealthIndicator.mediaService = "MediaKind";
        preApiHealthIndicator.ingestStorageAccountName = "ingest-account";
        preApiHealthIndicator.finalStorageAccountName = "final-account";

        when(mediaKindAccountsClient.getStorageAccounts())
            .thenReturn(MkStorageAccounts.builder()
                            .items(List.of(
                                createMkStorageAccount("ingest-account", "Running"),
                                createMkStorageAccount("final-account", "Running")
                            ))
                            .build());

        var res = preApiHealthIndicator.health();

        assertThat(res).isEqualTo(
            Health.up()
                .withDetail("mediakindConnections", Map.of(
                    "ingest-account", true,
                    "final-account", true
                )).build()
        );
    }

    @Test
    @DisplayName("Should return health and connections when not successful")
    void healthUpAndConnectionDetailsWhenNotSuccess() {
        preApiHealthIndicator.mediaService = "MediaKind";
        preApiHealthIndicator.ingestStorageAccountName = "ingest-account";
        preApiHealthIndicator.finalStorageAccountName = "final-account";

        when(mediaKindAccountsClient.getStorageAccounts())
            .thenReturn(MkStorageAccounts.builder()
                            .items(List.of(
                                createMkStorageAccount("ingest-account", "NotRunning"),
                                createMkStorageAccount("final-account", null)
                            ))
                            .build());

        var res = preApiHealthIndicator.health();

        assertThat(res).isEqualTo(
            Health.up()
                .withDetail("mediakindConnections", Map.of(
                    "ingest-account", false,
                    "final-account", false
                )).build());
    }

    @Test
    @DisplayName("Should return health and connections when MK has no connections")
    void healthUpAndConnectionDetailsWhenNoConnections() {
        preApiHealthIndicator.mediaService = "MediaKind";
        preApiHealthIndicator.ingestStorageAccountName = "ingest-account";
        preApiHealthIndicator.finalStorageAccountName = "final-account";
        when(mediaKindAccountsClient.getStorageAccounts())
            .thenReturn(MkStorageAccounts.builder().items(List.of()).build());

        var res = preApiHealthIndicator.health();

        assertThat(res).isEqualTo(
            Health.up()
            .withDetail("mediakindConnections", Map.of(
                "ingest-account", false,
                "final-account", false
            )).build());
    }

    private MkStorageAccount createMkStorageAccount(String name, String linkStatus) {
        return MkStorageAccount.builder()
                    .status(MkStorageAccount.MkStorageAccountStatus.builder()
                                .activeCredentialId(UUID.randomUUID())
                                .privateLinkServiceConnectionStatus(linkStatus)
                                .build())
                    .spec(MkStorageAccount.MkStorageAccountSpec.builder()
                              .name(name)
                              .build())
            .build();
    }
}
