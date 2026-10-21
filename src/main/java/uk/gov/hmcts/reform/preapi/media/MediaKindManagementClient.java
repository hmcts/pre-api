package uk.gov.hmcts.reform.preapi.media;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import uk.gov.hmcts.reform.preapi.config.MediaKindClientConfiguration;

@FeignClient(
    name = "mediaKindManagementClient",
    url = "${mediakind.management-api}",
    configuration = MediaKindClientConfiguration.class
)
public interface MediaKindManagementClient {

    @GetMapping("/api/ready")
    void getReady();

    @GetMapping("/api/v1/user/profile")
    Object getProfile();
}
