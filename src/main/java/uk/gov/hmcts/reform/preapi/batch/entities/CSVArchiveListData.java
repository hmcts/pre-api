package uk.gov.hmcts.reform.preapi.batch.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class CSVArchiveListData {
    private static final Logger logger = LoggerFactory.getLogger(CSVArchiveListData.class);
    
    private static final List<String> DATE_PATTERNS = List.of(
        "dd/MM/yyyy HH:mm", "dd/MM/yyyy H:mm",
        "d/MM/yyyy HH:mm", "d/MM/yyyy H:mm",
        "dd/M/yyyy HH:mm", "dd/M/yyyy H:mm",
        "d/M/yyyy HH:mm", "d/M/yyyy H:mm",
        "dd-MM-yyyy HH:mm", "dd-MM-yyyy H:mm",
        "d-MM-yyyy HH:mm", "d-MM-yyyy H:mm",
        "dd-M-yyyy HH:mm", "dd-M-yyyy H:mm",
        "d-M-yyyy HH:mm", "d-M-yyyy H:mm"
    );

    private String archiveName = "";
    private String createTime = "";
    private Integer duration = 0;
    private String fileName = "";
    private String fileSize = "";

    public CSVArchiveListData() {
    }

    public CSVArchiveListData(String archiveName, String createTime, Integer duration, String fileName, String fileSize) {
        this.archiveName = archiveName;
        this.createTime = createTime;
        this.duration = (duration != null) ? duration : 0;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public String getArchiveName() {
        return archiveName;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = (duration != null) ? duration : 0;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSize() {
        return fileSize;
    }

    public void setFileSize(String fileSize) {
        this.fileSize = fileSize;
    }

    public String getArchiveNameNoExt() {
        if (archiveName == null || archiveName.isEmpty()) {
            return archiveName;
        }
        
        int lastDotIndex = archiveName.lastIndexOf('.');
        return (lastDotIndex == -1) ? archiveName : archiveName.substring(0, lastDotIndex);
    }

    public LocalDateTime getCreateTimeAsLocalDateTime() {
        if (createTime == null || createTime.isEmpty()) {
            return null;
        }

        try {
            long timestamp = Long.parseLong(createTime.trim());
            return LocalDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(timestamp),
                java.time.ZoneId.systemDefault()
            );

        } catch (NumberFormatException e){
            return DATE_PATTERNS.stream()
                .map(pattern -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.UK);
                        return LocalDateTime.parse(createTime.trim(), formatter);
                    } catch (DateTimeParseException ex) {
                        logger.debug("Failed to parse date '{}' with pattern '{}'", createTime, pattern);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElseGet(() -> {
                    logger.error("Could not parse create time '{}' with any known pattern", createTime);
                    return null;
            });
        }

        
    }

    @Override
    public String toString() {
        return "CSVArchiveListData{" +
               "archiveName='" + archiveName + '\'' +
               ", createTime='" + createTime + '\'' +
               ", duration=" + duration +
               ", fileName='" + fileName + '\'' +
               ", fileSize='" + fileSize + '\'' +
               '}';
    }
}
