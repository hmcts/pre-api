package uk.gov.hmcts.reform.preapi.media;

import com.azure.core.management.exception.ManagementException;
import com.azure.resourcemanager.mediaservices.fluent.AzureMediaServices;
import com.azure.resourcemanager.mediaservices.fluent.models.AssetInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveEventInner;
import com.azure.resourcemanager.mediaservices.fluent.models.LiveOutputInner;
import com.azure.resourcemanager.mediaservices.models.Hls;
import com.azure.resourcemanager.mediaservices.models.IpAccessControl;
import com.azure.resourcemanager.mediaservices.models.IpRange;
import com.azure.resourcemanager.mediaservices.models.LiveEventEndpoint;
import com.azure.resourcemanager.mediaservices.models.LiveEventInput;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventInputProtocol;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreview;
import com.azure.resourcemanager.mediaservices.models.LiveEventPreviewAccessControl;
import com.azure.resourcemanager.mediaservices.models.LiveEventResourceState;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.media.AssetDTO;
import uk.gov.hmcts.reform.preapi.dto.media.LiveEventDTO;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UnknownServerException;
import uk.gov.hmcts.reform.preapi.repositories.CaptureSessionRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Service
public class AzureMediaService implements IMediaService {
    private final String resourceGroup;
    private final String accountName;
    private final String ingestStorageAccount;
    private final String environmentTag;
    private final AzureMediaServices amsClient;
    private final CaptureSessionRepository captureSessionRepository;
    private final UserRepository userRepository;

    private static final String LOCATION = "uksouth";

    @Autowired
    public AzureMediaService(
        @Value("${azure.resource-group}") String resourceGroup,
        @Value("${azure.account-name}") String accountName,
        @Value("${azure.ingestStorage}") String ingestStorageAccount,
        @Value("${azure.environmentTag}") String env,
        AzureMediaServices amsClient,
        CaptureSessionRepository captureSessionRepository,
        UserRepository userRepository) {
        this.resourceGroup = resourceGroup;
        this.accountName = accountName;
        this.ingestStorageAccount = ingestStorageAccount;
        this.environmentTag = env;
        this.amsClient = amsClient;
        this.captureSessionRepository = captureSessionRepository;
        this.userRepository = userRepository;
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
        // assetName is an id without the '-'
        return tryAmsRequest(
            () -> new AssetDTO(amsClient.getAssets().get(resourceGroup, accountName, assetName)),
            Map.of(
                404, e -> {
                    throw new NotFoundException("Asset with name: " + assetName);
                }
            )
        );

    }

    @Override
    public List<AssetDTO> getAssets() {
        return tryAmsRequest(
            () -> amsClient
                .getAssets()
                .list(resourceGroup, accountName)
                .stream()
                .map(AssetDTO::new)
                .toList(),
            Map.of()
        );
    }

    @Override
    public LiveEventDTO getLiveEvent(String liveEventName) {
        try {
            return new LiveEventDTO(amsClient.getLiveEvents().get(resourceGroup, accountName, liveEventName));
        } catch (ManagementException e) {
            if (e.getResponse().getStatusCode() == 404) {
                throw new NotFoundException("Live event: " + liveEventName);
            }
            throw e;
        }
    }

    @Override
    public List<LiveEventDTO> getLiveEvents() {
        return amsClient
            .getLiveEvents()
            .list(resourceGroup, accountName)
            .stream()
            .map(LiveEventDTO::new)
            .toList();
    }

    @Override
    @Transactional(dontRollbackOn = Exception.class)
    @PreAuthorize("@authorisationService.hasCaptureSessionAccess(authentication, #captureSessionId)")
    public CaptureSessionDTO startLiveEvent(UUID captureSessionId) {
        var captureSession = captureSessionRepository
            .findByIdAndDeletedAtIsNull(captureSessionId)
            .orElseThrow(() -> new NotFoundException("Capture Session: " + captureSessionId));

        if (captureSession.getFinishedAt() != null) {
            throw new ConflictException("Capture Session: " + captureSession.getId() + " has already been finished");
        }

        if (captureSession.getStartedAt() != null) {
            return new CaptureSessionDTO(captureSession);
        }

        var userId = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication()).getUserId();
        var user = userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User: " + userId));

        captureSession.setStartedByUser(user);
        captureSession.setStartedAt(Timestamp.from(Instant.now()));
        captureSessionRepository.saveAndFlush(captureSession);

        try {
            var liveEventName = uuidToNameString(captureSessionId);
            createLiveEvent(captureSession);
            getLiveEventAms(liveEventName);
            createAsset(liveEventName, captureSession);
            createLiveOutput(liveEventName, liveEventName);
            startLiveEvent(liveEventName);
            var liveEvent = checkStreamReady(liveEventName);

            // get ingest url (rtmps)
            var inputRtmp = Stream.ofNullable(liveEvent.input().endpoints())
                .flatMap(Collection::stream)
                .filter(e -> e.protocol().equals("RTMP") && e.url().startsWith("rtmps://"))
                .findFirst()
                .map(LiveEventEndpoint::url)
                .orElse(null);

            captureSession.setStatus(RecordingStatus.STANDBY);
            captureSession.setIngestAddress(inputRtmp);
            captureSessionRepository.saveAndFlush(captureSession);

            return new CaptureSessionDTO(captureSession);
        } catch (InterruptedException e) {
            throw new UnknownServerException("Something went wrong when attempting to communicate with Azure");
        } catch (Exception e) {
            captureSession.setStatus(RecordingStatus.FAILURE);
            captureSessionRepository.saveAndFlush(captureSession);
            throw e;
        }
    }

    private void startLiveEvent(String liveEventName) {
        tryAmsRequest(
            () -> {
                amsClient.getLiveEvents().start(resourceGroup, accountName, liveEventName);
                return null;
            },
            Map.of(
                404, e -> {
                    throw new NotFoundException("Live Event: " + liveEventName);
                }
            )
        );
    }

    private LiveEventInner checkStreamReady(String liveEventName) throws InterruptedException {
        LiveEventInner liveEvent;
        do {
            TimeUnit.MILLISECONDS.sleep(2000); // wait 2 seconds
            liveEvent = getLiveEventAms(liveEventName);
        } while (!liveEvent.resourceState().equals(LiveEventResourceState.RUNNING));
        return liveEvent;
    }

    private LiveEventInner getLiveEventAms(String liveEventName) {
        return tryAmsRequest(
            () -> amsClient.getLiveEvents().get(resourceGroup, accountName, liveEventName),
            Map.of(
                404, e -> {
                    throw new NotFoundException("Live event: " + liveEventName);
                }
            )
        );
    }

    private void createLiveOutput(String liveEventName, String liveOutputName) {
        tryAmsRequest(
            () -> amsClient.getLiveOutputs().create(
                resourceGroup,
                accountName,
                liveEventName,
                liveOutputName,
                new LiveOutputInner()
                    .withDescription("Live output for: " + liveEventName)
                    .withAssetName(liveEventName)
                    .withArchiveWindowLength(Duration.ofHours(8))
                    .withHls(new Hls().withFragmentsPerTsSegment(5))
                    .withManifestName(liveEventName)
            ),
            Map.of(
                409, e -> {
                    throw new ConflictException("Live Output: " + liveOutputName);
                },
                404, e -> {
                    throw new NotFoundException("Live Event: " + liveEventName);
                }
            )
        );
    }

    private AssetInner createAsset(String assetName, CaptureSession captureSession) {
        return tryAmsRequest(
            () -> amsClient
                .getAssets()
                .createOrUpdate(
                    resourceGroup,
                    accountName,
                    assetName,
                    new AssetInner()
                        .withContainer(captureSession.getBooking().getId().toString())
                        .withStorageAccountName(ingestStorageAccount)
                        .withDescription(captureSession.getBooking().getId().toString())
                ),
            Map.of(
                409, e -> {
                    throw new ConflictException("Asset: " + assetName);
                }
            )
        );
    }

    private void createLiveEvent(CaptureSession captureSession) {
        var accessToken = UUID.randomUUID();
        tryAmsRequest(
            () -> amsClient.getLiveEvents().create(
                resourceGroup,
                accountName,
                uuidToNameString(captureSession.getId()),
                new LiveEventInner()
                    .withLocation(LOCATION)
                    .withTags(Map.of(
                        "environment", environmentTag,
                        "application", "pre-recorded evidence",
                        "businessArea", "cross-cutting",
                        "builtFrom", "pre-api"
                    ))
                    .withDescription(captureSession.getBooking().getId().toString())
                    .withUseStaticHostname(true)
                    .withInput(
                        new LiveEventInput()
                            .withStreamingProtocol(LiveEventInputProtocol.RTMP)
                            .withKeyFrameIntervalDuration("PT6S")
                            .withAccessToken(accessToken.toString())
                            .withAccessControl(
                                new LiveEventInputAccessControl()
                                    .withIp(new IpAccessControl()
                                                .withAllow(List.of(new IpRange()
                                                                       .withName("AllowAll")
                                                                       .withAddress("0.0.0.0")
                                                                       .withSubnetPrefixLength(0)))
                                    )))
                    .withPreview(
                        new LiveEventPreview()
                            .withAccessControl(
                                new LiveEventPreviewAccessControl()
                                    .withIp(new IpAccessControl()
                                                .withAllow(List.of(new IpRange()
                                                                       .withName("AllowAll")
                                                                       .withAddress("0.0.0.0")
                                                                       .withSubnetPrefixLength(0)
                                                )))
                            ))),
            Map.of(
                409, (e) -> {
                    // already exists so do nothing
                }
            )
        );
    }

    private String uuidToNameString(UUID id) {
        return id.toString().replaceAll("-", "");
    }

    private <E> E tryAmsRequest(RunAmsRequestFunction<E> requestFunction,
                                Map<Integer, Consumer<Exception>> onErrorResponse) {
        try {
            return requestFunction.run();
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Unable to communicate with Azure");
        } catch (ManagementException e) {
            onErrorResponse
                .keySet()
                .stream()
                .filter(k -> e.getResponse().getStatusCode() == k)
                .findFirst()
                .ifPresentOrElse(
                    key -> onErrorResponse.get(key).accept(e),
                    () -> {
                        throw e;
                    }
                );
        }
        return null;
    }

    @FunctionalInterface
    protected interface RunAmsRequestFunction<E> {
        E run();
    }
}
