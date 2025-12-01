package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.media.edit.FfmpegService;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public abstract class BaseCleanupNullRecordingDurationTest {
    @MockitoBean
    protected RecordingService recordingService;

    @MockitoBean
    protected AzureFinalStorageService azureFinalStorageService;

    @MockitoBean
    protected FfmpegService ffmpegService;

    @MockitoBean
    protected UserService userService;

    @MockitoBean
    protected UserAuthenticationService userAuthenticationService;

    @Autowired
    protected CleanupNullRecordingDuration cleanupNullRecordingDuration;

    protected static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void beforeEach() {
        BaseAppAccessDTO appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        AccessDTO access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);

        UserAuthentication userAuth = mock(UserAuthentication.class);
        when(userAuth.getUserId()).thenReturn(UUID.randomUUID());
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(userAuth));
    }
}
