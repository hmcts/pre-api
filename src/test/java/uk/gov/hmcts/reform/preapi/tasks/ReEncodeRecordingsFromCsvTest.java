package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReEncodeRecordingsFromCsvTest {

    private static final String CRON_USER_EMAIL = "cron@example.com";

    private final EditRequestService editRequestService = mock(EditRequestService.class);
    private final UserService userService = mock(UserService.class);
    private final UserAuthenticationService userAuthenticationService = mock(UserAuthenticationService.class);

    @TempDir
    private Path tempDir;

    @BeforeEach
    void setUp() {
        var appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());

        var access = new AccessDTO();
        access.setAppAccess(Set.of(appAccess));

        when(userService.findByEmail(CRON_USER_EMAIL)).thenReturn(access);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.of(mock(UserAuthentication.class)));
        when(editRequestService.findRecordingIdsWithForceReencodeRequests(anySet())).thenReturn(Set.of());
    }

    @Test
    @DisplayName("Submits force re-encode edit requests from CSV without notifications")
    void submitsReencodeRequestsFromCsvWithoutNotifications() throws IOException {
        UUID firstRecordingId = UUID.randomUUID();
        UUID secondRecordingId = UUID.randomUUID();
        Path csv = writeCsv("""
            source_recording_id,case_reference
            %s,CASE-123
            %s,CASE-456
            """.formatted(firstRecordingId, secondRecordingId));

        task(csv, false).run();

        var dtoCaptor = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestService, times(2)).upsert(dtoCaptor.capture());

        List<CreateEditRequestDTO> requests = dtoCaptor.getAllValues();
        assertThat(requests).extracting(CreateEditRequestDTO::getSourceRecordingId)
            .containsExactly(firstRecordingId, secondRecordingId);
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.getId()).isNotNull();
            assertThat(request.getStatus()).isEqualTo(EditRequestStatus.PENDING);
            assertThat(request.getEditInstructions()).isEmpty();
            assertThat(request.isForceReencode()).isTrue();
            assertThat(request.getSendNotifications()).isFalse();
        });
    }

    @Test
    @DisplayName("Skips duplicate CSV rows and existing force re-encode requests by default")
    void skipsDuplicateRowsAndExistingReencodeRequestsByDefault() throws IOException {
        UUID duplicateRecordingId = UUID.randomUUID();
        UUID existingRecordingId = UUID.randomUUID();
        Path csv = writeCsv("""
            source_recording_id,case_reference
            %s,CASE-123
            %s,CASE-123
            %s,CASE-456
            """.formatted(duplicateRecordingId, duplicateRecordingId, existingRecordingId));
        when(editRequestService.findRecordingIdsWithForceReencodeRequests(anySet()))
            .thenReturn(Set.of(existingRecordingId));

        task(csv, false).run();

        var dtoCaptor = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestService).upsert(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getSourceRecordingId()).isEqualTo(duplicateRecordingId);
    }

    @Test
    @DisplayName("Force flag submits requests even when a previous force re-encode exists")
    void forceFlagSubmitsRequestsEvenWhenPreviousReencodeExists() throws IOException {
        UUID recordingId = UUID.randomUUID();
        Path csv = writeCsv("""
            source_recording_id,case_reference
            %s,CASE-123
            """.formatted(recordingId));

        task(csv, true).run();

        var dtoCaptor = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestService).upsert(dtoCaptor.capture());
        verify(editRequestService, never()).findRecordingIdsWithForceReencodeRequests(anySet());
        assertThat(dtoCaptor.getValue().getSourceRecordingId()).isEqualTo(recordingId);
    }

    @Test
    @DisplayName("Reads re-encode CSV from classpath resource")
    void readsCsvFromClasspathResource() {
        task("classpath:re-encode-recordings.csv", false).run();

        var dtoCaptor = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestService).upsert(dtoCaptor.capture());
        assertThat(dtoCaptor.getValue().getSourceRecordingId())
            .isEqualTo(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("Skips invalid recording ids without submitting an edit request")
    void skipsInvalidRecordingIds() throws IOException {
        Path csv = writeCsv("""
            source_recording_id,case_reference
            not-a-uuid,CASE-123
            ,CASE-456
            """);

        task(csv, false).run();

        verify(editRequestService).findRecordingIdsWithForceReencodeRequests(Set.of());
        verify(editRequestService, never()).upsert(any());
    }

    @Test
    @DisplayName("Continues processing when an edit request cannot be submitted")
    void continuesProcessingWhenSubmitFails() throws IOException {
        UUID failingRecordingId = UUID.randomUUID();
        UUID successfulRecordingId = UUID.randomUUID();
        Path csv = writeCsv("""
            source_recording_id,case_reference
            %s,
            %s,CASE-456
            """.formatted(failingRecordingId, successfulRecordingId));
        when(editRequestService.upsert(any(CreateEditRequestDTO.class)))
            .thenThrow(new IllegalStateException("failed"))
            .thenReturn(UpsertResult.CREATED);

        task(csv, false).run();

        var dtoCaptor = ArgumentCaptor.forClass(CreateEditRequestDTO.class);
        verify(editRequestService, times(2)).upsert(dtoCaptor.capture());
        assertThat(dtoCaptor.getAllValues()).extracting(CreateEditRequestDTO::getSourceRecordingId)
            .containsExactly(failingRecordingId, successfulRecordingId);
    }

    @Test
    @DisplayName("Throws when the CSV path is not configured")
    void throwsWhenCsvPathIsNotConfigured() {
        ReEncodeRecordingsFromCsv task = task("", false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, task::run);

        assertThat(exception.getMessage()).isEqualTo("Failed to read re-encode CSV file");
        verifyNoInteractions(editRequestService);
    }

    @Test
    @DisplayName("Throws when the CSV file does not exist")
    void throwsWhenCsvFileDoesNotExist() {
        ReEncodeRecordingsFromCsv task = task(tempDir.resolve("missing.csv").toString(), false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, task::run);

        assertThat(exception.getMessage()).isEqualTo("Failed to read re-encode CSV file");
        verifyNoInteractions(editRequestService);
    }

    private ReEncodeRecordingsFromCsv task(Path csv, boolean forceReencode) {
        return task(csv.toString(), forceReencode);
    }

    private ReEncodeRecordingsFromCsv task(String csvPath, boolean forceReencode) {
        return new ReEncodeRecordingsFromCsv(
            editRequestService,
            userService,
            userAuthenticationService,
            new DefaultResourceLoader(),
            CRON_USER_EMAIL,
            csvPath,
            forceReencode
        );
    }

    private Path writeCsv(String contents) throws IOException {
        Path csv = tempDir.resolve("re-encode-recordings.csv");
        Files.writeString(csv, contents);
        return csv;
    }
}
