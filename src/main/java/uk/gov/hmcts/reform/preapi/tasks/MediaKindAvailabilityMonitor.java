package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.media.MediaKindClient;
import uk.gov.hmcts.reform.preapi.media.MediaKindManagementClient;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.util.Locale;
import java.util.UUID;

@Component
@Slf4j
@ConditionalOnProperty(
    name = "mediakind.monitor.enabled",
    havingValue = "true"
)
public class MediaKindAvailabilityMonitor {

    private static final String READY_ENDPOINT = "/api/ready";
    private static final String PROFILE_ENDPOINT = "/api/v1/user/profile";
    private static final String ASSETS_ENDPOINT = "/assets";
    private static final String VOD_ENDPOINT = "/assets/{assetName}";

    private final MediaKindManagementClient mediaKindManagementClient;
    private final MediaKindClient mediaKindClient;
    private final RecordingRepository recordingRepository;
    private final UUID testCourtId;

    public MediaKindAvailabilityMonitor(
        MediaKindManagementClient mediaKindManagementClient,
        MediaKindClient mediaKindClient,
        RecordingRepository recordingRepository,
        @Value("${mediakind.monitor.test-court-id}") UUID testCourtId
    ) {
        this.mediaKindManagementClient = mediaKindManagementClient;
        this.mediaKindClient = mediaKindClient;
        this.recordingRepository = recordingRepository;
        this.testCourtId = testCourtId;
    }

    @Scheduled(fixedDelayString = "${mediakind.monitor.ready-interval-ms:30000}")
    public void checkAvailability() {
        String checkId = UUID.randomUUID().toString();

        runReadyAndProfileFallback(checkId);
        runAssetsAvailabilityCheck(checkId);
        runVodAvailabilityCheck(checkId);
    }

    private void runReadyAndProfileFallback(String checkId) {
        long readyStart = System.currentTimeMillis();
        try {
            mediaKindManagementClient.getReady();
            long durationMs = System.currentTimeMillis() - readyStart;
            log.info("MK.IO API readiness check succeeded. checkId={} endpoint={} durationMs={}",
                     checkId, READY_ENDPOINT, durationMs);
            return;
        } catch (Exception readyEx) {
            long durationMs = System.currentTimeMillis() - readyStart;
            log.error(
                "MK.IO API readiness check failed. checkId={} endpoint={} "
                    + "durationMs={} failureType={} error={}",
                checkId,
                READY_ENDPOINT,
                durationMs,
                classify(readyEx),
                exceptionMessage(readyEx),
                readyEx
            );
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
                      checkId, PROFILE_ENDPOINT, durationMs, classify(profileEx), exceptionMessage(profileEx),
                      profileEx);
        }
    }

    private void runAssetsAvailabilityCheck(String checkId) {
        long assetsStart = System.currentTimeMillis();
        try {
            mediaKindClient.getAssets(0, 10);
            long durationMs = System.currentTimeMillis() - assetsStart;
            log.info("MK.IO assets health check succeeded. checkId={} endpoint={} durationMs={}",
                     checkId, ASSETS_ENDPOINT, durationMs);
        } catch (Exception assetsEx) {
            long durationMs = System.currentTimeMillis() - assetsStart;
            log.error("MK.IO assets health check failed. checkId={} endpoint={} durationMs={} failureType={} error={}",
                      checkId, ASSETS_ENDPOINT, durationMs, classify(assetsEx), exceptionMessage(assetsEx), assetsEx);
        }
    }

    private void runVodAvailabilityCheck(String checkId) {
        long vodStart = System.currentTimeMillis();

        try {
            UUID recordingId = recordingRepository
                .findRecordingIdsByCourtId(testCourtId, PageRequest.of(0, 1))
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "No recording found for configured MediaKind monitor court"
                ));

            mediaKindClient.getAsset(recordingId.toString());

            long durationMs = System.currentTimeMillis() - vodStart;
            log.info(
                "MK.IO VOD health check succeeded. checkId={} endpoint={} recordingId={} durationMs={}",
                checkId,
                VOD_ENDPOINT,
                recordingId,
                durationMs
            );
        } catch (Exception vodEx) {
            long durationMs = System.currentTimeMillis() - vodStart;
            log.error(
                "MK.IO VOD health check failed. checkId={} endpoint={} courtId={} "
                    + "durationMs={} failureType={} error={}",
                checkId,
                VOD_ENDPOINT,
                testCourtId,
                durationMs,
                classify(vodEx),
                exceptionMessage(vodEx),
                vodEx
            );
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
