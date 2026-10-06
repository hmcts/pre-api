package uk.gov.hmcts.reform.preapi.tasks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.media.MediaKindManagementClient;

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MediaKindAvailabilityMonitor {

    private static final String READY_ENDPOINT = "/api/ready";
    private static final String PROFILE_ENDPOINT = "/api/v1/user/profile";

    private final MediaKindManagementClient mediaKindManagementClient;

    @Scheduled(fixedDelayString = "${mediakind.monitor.ready-interval-ms}")
    public void checkAvailability() {
        String checkId = UUID.randomUUID().toString();

        long readyStart = System.currentTimeMillis();
        try {
            mediaKindManagementClient.getReady();
            long durationMs = System.currentTimeMillis() - readyStart;
            log.info("MK.IO API readiness check succeeded. checkId={} endpoint={} durationMs={}",
                     checkId, READY_ENDPOINT, durationMs);
            return;
        } catch (Exception readyEx) {
            long durationMs = System.currentTimeMillis() - readyStart;
            log.error("MK.IO API readiness check failed. checkId={} endpoint={} durationMs={} failureType={} error={}",
                      checkId, READY_ENDPOINT, durationMs, classify(readyEx), exceptionMessage(readyEx), readyEx);
        }

        long profileStart = System.currentTimeMillis();
        try {
            mediaKindManagementClient.getProfile();
            long durationMs = System.currentTimeMillis() - profileStart;
            log.warn("MK.IO profile fallback succeeded after readiness failure. checkId={} endpoint={} durationMs={}",
                     checkId, PROFILE_ENDPOINT, durationMs);
        } catch (Exception profileEx) {
            long durationMs = System.currentTimeMillis() - profileStart;
            log.error("MK.IO profile fallback failed. checkId={} endpoint={} durationMs={} failureType={} error={}",
                      checkId, PROFILE_ENDPOINT, durationMs, classify(profileEx),
                      exceptionMessage(profileEx), profileEx);
        }
    }

    private String classify(Exception ex) {
        String simpleName = ex.getClass().getSimpleName().toLowerCase(Locale.ROOT);

        if (simpleName.contains("timeout")) {
            return "timeout";
        }
        if (simpleName.contains("connect") || simpleName.contains("socket") || simpleName.contains("unknownhost")) {
            return "connection";
        }
        if (simpleName.contains("feign")) {
            return "http_error";
        }
        return "unexpected_error";
    }

    private String exceptionMessage(Exception ex) {
        return ex.getMessage() == null ? "<no exception message>" : ex.getMessage();
    }
}
