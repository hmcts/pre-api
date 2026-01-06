package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;
import uk.gov.hmcts.reform.preapi.media.dto.MkStorageAccounts;

@FeignClient(
    name = "mediaKindAccountsClient",
    url = "${mediakind.accountsApi}",
    configuration = MediaKindClientConfiguration.class
)
@SuppressWarnings("PMD.ImplicitFunctionalInterface")
public interface MediaKindAccountsClient {
    @GetMapping("/subscriptions/${mediakind.subscription-id}/storage")
    MkStorageAccounts getStorageAccounts();
}
