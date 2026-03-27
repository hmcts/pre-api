package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingReencodeJobService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = QueueVodafoneRecordingReencode.class)
class QueueVodafoneRecordingReencodeTest {

    @MockitoBean
    private RecordingReencodeJobService recordingReencodeJobService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    private QueueVodafoneRecordingReencode underTest;

    private static final String CRON_USER_EMAIL = "test@test.com";

    @BeforeEach
    void setUp() {
        underTest = new QueueVodafoneRecordingReencode(
            recordingReencodeJobService,
            userService,
            userAuthenticationService,
            CRON_USER_EMAIL,
            "5fc8e8b5-e65c-4b24-a7f8-0d8e1f0e5c56,4b9fe1fa-d342-4ef1-a0ee-58b581f1a690"
        );

        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        var access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(userAuth));
    }

    @Test
    @DisplayName("QueueVodafoneRecordingReencode run should queue parsed recording ids")
    void runQueuesParsedIds() {
        underTest.run();

        verify(recordingReencodeJobService, times(1)).queueJobs(List.of(
            UUID.fromString("5fc8e8b5-e65c-4b24-a7f8-0d8e1f0e5c56"),
            UUID.fromString("4b9fe1fa-d342-4ef1-a0ee-58b581f1a690")
        ));
    }
}
