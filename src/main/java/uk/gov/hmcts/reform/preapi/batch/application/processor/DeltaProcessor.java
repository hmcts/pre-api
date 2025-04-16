package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeltaProcessor {
    private final LoggingService loggingService;

    @Autowired
    public DeltaProcessor(final LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void processDelta(String previousFile, String currentFile, String deltaFile) {
        try {
            List<String> headers = loadHeaders(currentFile);
            Map<String, String> previousRecords = loadRecords(previousFile);
            Map<String, String> newRecords = loadRecords(currentFile);

            newRecords.keySet().removeAll(previousRecords.keySet());

            writeNewRecords(deltaFile, headers, newRecords);
            loggingService.logInfo("New delta records written to: %s", deltaFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> loadHeaders(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            return Collections.singletonList(br.readLine());
        }
    }

    private Map<String, String> loadRecords(String filePath) throws IOException {
        Map<String, String> records = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String key = line.split(",")[0];
                records.put(key, line);
            }
        }
        return records;
    }

    private void writeNewRecords(String filePath, List<String> headers, Map<String, String> records)
        throws IOException {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String header : headers) {
                writer.write(header);
                writer.newLine();
            }
            for (String entry : records.values()) {
                writer.write(entry);
                writer.newLine();
            }
        }
    }
}
