package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class CsvWriterService {

    /**
     * Writes data to a CSV file.
     * @param headers        The headers for the CSV file.
     * @param dataRows       The data rows to write.
     * @param fileNamePrefix The prefix for the CSV file name.
     * @param outputDir      The directory to write the CSV file to.
     */
    public <T> void writeToCsv(
        List<String> headers, 
        List<T> dataRows, 
        String fileNamePrefix,
        String outputDir,
        boolean showTimestamp
    ) {
        Path outputPath = Paths.get(outputDir);
        if (!outputPath.toFile().exists()) {
            boolean dirCreated = outputPath.toFile().mkdirs();
            if (!dirCreated) {
                Logger.getAnonymousLogger().severe("Failed to create output directory: " + outputDir);
                return; 
            }
        }

        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH:mm:ss"));
        String fileName = showTimestamp ? fileNamePrefix + "-" + timeStamp + ".csv" : fileNamePrefix + ".csv";
        Path filePath = outputPath.resolve(fileName);

        boolean fileExists = new File(fileName).exists();
        try (FileWriter fileWriter = new FileWriter(filePath.toFile(), false)) {
            if (!fileExists) {
                fileWriter.write(String.join(",", headers) + "\n");
            }

            for (T dataRow : dataRows) {
                String line = formatDataRow(dataRow);
                fileWriter.write(line + "\n");
            }
            Logger.getAnonymousLogger().info("Successfully wrote " + dataRows.size() 
                + " rows to CSV file: " + filePath);
            
        } catch (IOException e) {
            Logger.getAnonymousLogger().severe("Error writing to CSV: " + e.getMessage());
        }
    }

    private <T> String formatDataRow(T dataRow) {
        if (dataRow instanceof List<?>) {
            List<?> list = (List<?>) dataRow;
            return list.stream()
                       .map(String::valueOf)  
                       .collect(Collectors.joining(",")); 
        } else {
            return dataRow.toString();
        }
    }
}
