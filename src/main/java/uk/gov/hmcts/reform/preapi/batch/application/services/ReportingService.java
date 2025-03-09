package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportingService {
    private static final Logger logger = LoggerFactory.getLogger(ReportingService.class);
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    /**
     * Writes data to a CSV file.
     * @param headers        The headers for the CSV file.
     * @param dataRows       The data rows to write.
     * @param fileNamePrefix The prefix for the CSV file name.
     * @param outputDir      The directory to write the CSV file to.
     * @param showTimestamp  Whether to include a timestamp in the file name.
     * @return The {@link Path} to the created CSV file.
     */
    public <T> Path writeToCsv(
        List<String> headers, 
        List<T> dataRows, 
        String fileNamePrefix,
        String outputDir,
        boolean showTimestamp
    ) throws IOException {
        if (headers == null || headers.isEmpty()) {
            throw new IllegalArgumentException("Headers cannot be null or empty");
        }
        if (dataRows == null) {
            throw new IllegalArgumentException("Data rows cannot be null");
        }
        if (fileNamePrefix == null || fileNamePrefix.trim().isEmpty()) {
            throw new IllegalArgumentException("File name prefix cannot be null or empty");
        }
        if (outputDir == null || outputDir.trim().isEmpty()) {
            throw new IllegalArgumentException("Output directory cannot be null or empty");
        }

        Path outputPath = Paths.get(outputDir);
        try {
            if (!Files.exists(outputPath)) {
                Files.createDirectories(outputPath);
                logger.info("Created report output directory: {}", outputDir);
            }
        } catch (Exception e) {
            logger.error("Failed to create output directory: {}", outputDir, e);
            throw e;
        }
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String fileName = showTimestamp 
            ? String.format("%s-%s.csv", fileNamePrefix, timestamp)
            : fileNamePrefix + ".csv";
        Path filePath = outputPath.resolve(fileName);

        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(String.join(",", headers));
            writer.newLine();

            for (T dataRow : dataRows) {
                writer.write(formatDataRow(dataRow));
                writer.newLine();
            }
        } 
        return filePath;      
    }

    private <T> String formatDataRow(T dataRow) {
        if (dataRow instanceof List<?>) {
            List<?> list = (List<?>) dataRow;
            return list.stream()
                .map(String::valueOf)  
                .collect(Collectors.joining(",")); 
        } 
        return dataRow.toString();
    }
}
