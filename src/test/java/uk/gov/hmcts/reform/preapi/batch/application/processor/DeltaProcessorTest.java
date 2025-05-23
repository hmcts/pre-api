package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringBootTest(classes = { DeltaProcessor.class })
public class DeltaProcessorTest {
    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private DeltaProcessor deltaProcessor;

    private String previousCsv;
    private String currentCsv;

    @BeforeEach
    void setUp() throws IOException {
        previousCsv = createTempCsvFile("previous", List.of("Id,Name\n1,One\n2,Two\n"));
        currentCsv = createTempCsvFile("current", List.of("Id,Name\n2,Two\n3,Three\n4,Four\n"));
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteTempFile(previousCsv);
        deleteTempFile(currentCsv);
    }

    @Test
    @DisplayName("Process delta")
    void processDelta() throws IOException {
        File tempFile = File.createTempFile("delta", ".csv");

        deltaProcessor.processDelta(previousCsv, currentCsv, tempFile.getAbsolutePath());

        assertThat(tempFile.exists()).isTrue();
        List<String> lines = readTempFile(tempFile.getAbsolutePath());
        assertThat(lines).hasSize(3);
        assertThat(lines.getFirst()).isEqualTo("Id,Name");
        assertThat(lines.get(1)).isEqualTo("3,Three");
        assertThat(lines.getLast()).isEqualTo("4,Four");

        verify(loggingService).logInfo("New delta records written to: %s", tempFile.getAbsolutePath());
    }

    private String createTempCsvFile(String fileName, List<String> content) throws IOException {
        Path tempFile = Files.createTempFile(fileName, ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(tempFile)) {
            for (String line : content) {
                writer.write(line);
                writer.newLine();
            }
        }

        return tempFile.toAbsolutePath().toString();
    }

    private void deleteTempFile(String filePath) throws IOException {
        Files.deleteIfExists(Path.of(filePath));
    }

    private List<String> readTempFile(String filePath) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        }

        return lines;
    }
}
