package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.InputStreamResource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4Metadata;
import uk.gov.hmcts.reform.preapi.batch.application.processor.VodafonePreferredMp4XmlParser;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = VodafonePreferredMp4ReportTask.class)
@TestPropertySource(properties = {
    "tasks.vodafone-preferred-mp4-report.container=test-container",
    "tasks.vodafone-preferred-mp4-report.working-directory=build/test-vf-preferred-mp4-report",
    "tasks.vodafone-preferred-mp4-report.file-limit=0",
    "vodafone-user-email=test@test.com"
})
class VodafonePreferredMp4ReportTaskTest {

    @MockitoBean
    private AzureVodafoneStorageService azureVodafoneStorageService;

    @MockitoBean
    private VodafonePreferredMp4XmlParser vodafonePreferredMp4XmlParser;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @Autowired
    private VodafonePreferredMp4ReportTask vodafonePreferredMp4ReportTask;

    @Value("${vodafone-user-email}")
    private String cronUserEmail;

    private final Path workingDirectory = Path.of("build/test-vf-preferred-mp4-report");

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
    @DisplayName("Should write an XML-only preferred MP4 report with UGC and Editor counts")
    void runWritesPreferredMp4Report() throws Exception {
        when(azureVodafoneStorageService.fetchBlobNames("test-container"))
            .thenReturn(List.of("pref/archive.xml"));
        when(azureVodafoneStorageService.fetchSingleXmlBlob("test-container", "pref/archive.xml"))
            .thenReturn(new InputStreamResource(new ByteArrayInputStream("<root/>".getBytes(StandardCharsets.UTF_8))));
        when(vodafonePreferredMp4XmlParser.parsePreferredMp4Files(any(), eq("pref"), eq("pref/archive.xml")))
            .thenReturn(List.of(
                new VodafonePreferredMp4Metadata("pref/archive.xml", "A1", "pref/A1/mp4/case_UGC.mp4", "case_UGC.mp4"),
                new VodafonePreferredMp4Metadata("pref/archive.xml", "A2", "pref/A2/mp4/case_Editor.mp4", "case_Editor.mp4"),
                new VodafonePreferredMp4Metadata("pref/archive.xml", "A3", "pref/A3/mp4/case_plain.mp4", "case_plain.mp4")
            ));

        vodafonePreferredMp4ReportTask.run();

        verify(azureVodafoneStorageService).fetchSingleXmlBlob("test-container", "pref/archive.xml");

        Path runDirectory = findSingleRunDirectory();
        String csvReport = Files.readString(runDirectory.resolve("preferred-mp4s.csv"));
        String summaryReport = Files.readString(runDirectory.resolve("summary.txt"));

        assertThat(csvReport).contains("\"case_UGC.mp4\",\"1\",\"true\",\"false\"");
        assertThat(csvReport).contains("\"case_Editor.mp4\",\"1\",\"false\",\"true\"");
        assertThat(csvReport).contains("\"case_plain.mp4\",\"1\",\"false\",\"false\"");

        assertThat(summaryReport).contains("Unique preferred MP4 blobs: 3");
        assertThat(summaryReport).contains("Preferred MP4s containing UGC: 1");
        assertThat(summaryReport).contains("Preferred MP4s containing Editor: 1");
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
