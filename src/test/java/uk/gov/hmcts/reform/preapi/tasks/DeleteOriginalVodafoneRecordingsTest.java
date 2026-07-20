package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = DeleteOriginalVodafoneRecordings.class)
class DeleteOriginalVodafoneRecordingsTest {

    private static final String CRON_USER_EMAIL = "cron@example.com";

    @MockitoBean
    private RecordingService recordingService;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private User user;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    UUID preRecordingId = UUID.randomUUID();
    UUID recordingWithReencodedVersion =  UUID.randomUUID();
    UUID recordingWithoutReencodedVersion =  UUID.randomUUID();

    @BeforeEach
    void setUp() {
        BaseAppAccessDTO appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());

        AccessDTO access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(mock(UserAuthentication.class)));

        when(recordingService.reencodedVersionExists(recordingWithReencodedVersion)).thenReturn(true);
        when(recordingService.reencodedVersionExists(recordingWithoutReencodedVersion)).thenReturn(false);
    }

    @Test
    @DisplayName("Nothing happens if Hide Reencodes flag is set to true")
    void nothingDeletedIfHideReencodesFlagIsSetToTrue() {
        DeleteOriginalVodafoneRecordings underTest = new DeleteOriginalVodafoneRecordings(recordingService,
                                                                                          userService,
                                                                                          userAuthenticationService,
                                                                                          CRON_USER_EMAIL,
                                                                                          "true");
        underTest.run();

        verifyNoInteractions(userService);
        verifyNoInteractions(userAuthenticationService);
        verifyNoInteractions(recordingService);
    }

    @Test
    @DisplayName("Delete original VF recordings where re-encoded version exists")
    void deleteVFWhereReEncodedVersionExists() {
        DeleteOriginalVodafoneRecordings underTest = new DeleteOriginalVodafoneRecordings(recordingService,
                                                                                          userService,
                                                                                          userAuthenticationService,
                                                                                          CRON_USER_EMAIL,
                                                                                          "false");

        RecordingDTO preRecording = new RecordingDTO();
        preRecording.setId(preRecordingId);

        RecordingDTO vfRecording = new RecordingDTO();
        vfRecording.setId(recordingWithoutReencodedVersion);

        RecordingDTO reencodedRecording = new RecordingDTO();
        reencodedRecording.setId(recordingWithReencodedVersion);

        when(recordingService.findAllVodafoneRecordings()).thenReturn(List.of(vfRecording, reencodedRecording));

        underTest.run();

        verify(recordingService, times(1)).reencodedVersionExists(recordingWithReencodedVersion);
        verify(recordingService, times(1)).reencodedVersionExists(recordingWithoutReencodedVersion);
        verify(recordingService, times(0)).reencodedVersionExists(preRecordingId);

        verify(recordingService, times(1)).deleteById(recordingWithReencodedVersion);
        verify(recordingService, times(0)).deleteById(recordingWithoutReencodedVersion);
        verify(recordingService, times(0)).deleteById(preRecordingId);

        verify(userService, times(1)).findByEmail(CRON_USER_EMAIL);

        verify(userAuthenticationService, times(1)).validateUser(any());
    }

}
