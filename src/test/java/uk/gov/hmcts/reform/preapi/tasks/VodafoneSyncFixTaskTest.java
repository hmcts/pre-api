package uk.gov.hmcts.reform.preapi.tasks;

import org.apache.commons.exec.CommandLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4Metadata;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4XmlParser;
import uk.gov.hmcts.reform.preapi.component.CommandExecutor;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.media.storage.AzureVodafoneStorageService;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = VodafoneSyncFixTask.class)
@TestPropertySource(properties = {
    "tasks.vodafone-sync-fix.container=test-container",
    "tasks.vodafone-sync-fix.script-path=bin/PRE_synch_fix.sh",
    "tasks.vodafone-sync-fix.working-directory=build/test-vf-sync-fix",
    "tasks.vodafone-sync-fix.dry-run=true",
    "tasks.vodafone-sync-fix.cleanup-downloads=false",
    "tasks.vodafone-sync-fix.file-limit=1",
    "vodafone-user-email=test@test.com"
})
class VodafoneSyncFixTaskTest {

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private CommandExecutor commandExecutor;

    @MockitoBean
    private VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @Autowired
    private VodafoneSyncFixTask vodafoneSyncFixTask;

    @Value("${vodafone-user-email}")
    private String cronUserEmail;

    private final Path workingDirectory = Path.of("build/test-vf-sync-fix");

    @BeforeEach
    void setUp() throws IOException {
        deleteDirectory(workingDirectory);

        AccessDTO access = new AccessDTO();
        BaseAppAccessDTO appAccess = new BaseAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        access.setAppAccess(Set.of(appAccess));

        UserAuthentication userAuthentication = mock(UserAuthentication.class);
        when(userService.findByEmail(cronUserEmail)).thenReturn(access);
        when(userAuthenticationService.validateUser(appAccess.getId().toString()))
            .thenReturn(Optional.of(userAuthentication));
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteDirectory(workingDirectory);
    }

    @Test
    @DisplayName("Should select the preferred Vodafone MP4, download it, and invoke the sync script in dry-run mode")
    void runUsesPreferredMp4SelectionAndWritesReports() throws Exception {
        when(azureVodafoneStorageService.fetchBlobNames("test-container"))
            .thenReturn(List.of("pref/archive.xml"));
        when(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "pref/archive.xml"))
            .thenReturn(new InputStreamResource(new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8))));
        when(vodafonePreferredMp4XmlParser.parsePreferredMp4Files(any(), eq("pref"), eq("pref/archive.xml")))
            .thenReturn(List.of(new VodafonePreferredMp4Metadata(
                "pref/archive.xml",
                "W1",
                "pref/W1/mp4/b.mp4",
                "b.mp4"
            )));
        when(azureVodafoneStorageService.downloadBlob(eq("test-container"), eq("pref/W1/mp4/b.mp4"), anyString()))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class)))
            .thenReturn("Summary\nFiles processed      : 1\nWould cut            : 1");

        vodafoneSyncFixTask.run();

        verify(azureVodafoneStorageService).downloadBlob(eq("test-container"), eq("pref/W1/mp4/b.mp4"), anyString());

        ArgumentCaptor<CommandLine> commandLineCaptor = ArgumentCaptor.forClass(CommandLine.class);
        verify(commandExecutor).executeAndGetOutput(commandLineCaptor.capture());
        String command = commandLineCaptor.getValue().toString();
        assertThat(command).contains("bash");
        assertThat(command).contains("PRE_synch_fix.sh");
        assertThat(command).contains("--dry-run");
        assertThat(command).contains("sync-fix-dry-run.csv");

        Path runDirectory = findSingleRunDirectory();
        String selectionReport = Files.readString(runDirectory.resolve("selected-mp4s.csv"));
        String summaryReport = Files.readString(runDirectory.resolve("summary.txt"));

        assertThat(selectionReport).contains("\"pref/W1/mp4/b.mp4\"");
        assertThat(selectionReport).contains("\"downloaded\"");
        assertThat(summaryReport).contains("Unique preferred MP4 blobs: 1");
        assertThat(summaryReport).contains("Downloaded successfully: 1");
        assertThat(summaryReport).contains("Would cut            : 1");
    }

    @Test
    @DisplayName("Should stop processing after the configured file limit is reached")
    void runStopsAfterConfiguredFileLimit() throws Exception {
        when(azureVodafoneStorageService.fetchBlobNames("test-container"))
            .thenReturn(List.of("pref/first.xml", "pref/second.xml"));
        when(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "pref/first.xml"))
            .thenReturn(new InputStreamResource(new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8))));
        when(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "pref/second.xml"))
            .thenReturn(new InputStreamResource(new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8))));
        when(vodafonePreferredMp4XmlParser.parsePreferredMp4Files(any(), eq("pref"), eq("pref/first.xml")))
            .thenReturn(List.of(new VodafonePreferredMp4Metadata(
                "pref/first.xml",
                "W1",
                "pref/W1/mp4/first.mp4",
                "first.mp4"
            )));
        when(azureVodafoneStorageService.downloadBlob(eq("test-container"), eq("pref/W1/mp4/first.mp4"), anyString()))
            .thenReturn(true);
        when(commandExecutor.executeAndGetOutput(any(CommandLine.class)))
            .thenReturn("Summary\nFiles processed      : 1\nWould cut            : 1");

        vodafoneSyncFixTask.run();

        verify(azureVodafoneStorageService).fetchSingleXmlBlob("test-container", "pref/first.xml");
        verify(azureVodafoneStorageService, org.mockito.Mockito.never())
            .fetchSingleXmlBlob("test-container", "pref/second.xml");
        verify(azureVodafoneStorageService).downloadBlob(eq("test-container"), eq("pref/W1/mp4/first.mp4"), anyString());
        verify(azureVodafoneStorageService, org.mockito.Mockito.never())
            .downloadBlob(eq("test-container"), eq("pref/W2/mp4/second.mp4"), anyString());

        Path runDirectory = findSingleRunDirectory();
        String summaryReport = Files.readString(runDirectory.resolve("summary.txt"));

        assertThat(summaryReport).contains("File limit: 1");
        assertThat(summaryReport).contains("File limit reached: true");
        assertThat(summaryReport).contains("XML blobs scanned: 1");
        assertThat(summaryReport).contains("Unique preferred MP4 blobs: 1");
    }

    private Path findSingleRunDirectory() throws IOException {
        try (Stream<Path> paths = Files.list(workingDirectory)) {
            return paths.findFirst().orElseThrow();
        }
    }

    private void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
