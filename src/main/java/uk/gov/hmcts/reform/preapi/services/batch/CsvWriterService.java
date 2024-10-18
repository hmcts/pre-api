package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@Service
public class CsvWriterService {

    public <T> void writeToCsv(List<String> headers, List<T> dataRows, String fileNamePrefix) {
        String timeStamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String fileName = fileNamePrefix + "-" + timeStamp + ".csv";

        boolean fileExists = new File(fileName).exists();

        try (FileWriter fileWriter = new FileWriter(fileName, true)) {

            if (!fileExists) {
                fileWriter.write(String.join(",", headers) + "\n");
            }

            for (T dataRow : dataRows) {
                String line = formatDataRow(dataRow);
                fileWriter.write(line + "\n");
            }

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
