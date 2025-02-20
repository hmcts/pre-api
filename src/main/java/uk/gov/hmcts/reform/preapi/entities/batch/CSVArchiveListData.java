package uk.gov.hmcts.reform.preapi.entities.batch;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class CSVArchiveListData {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    
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
        if (lastDotIndex == -1) {
            return archiveName;
        }
        return archiveName.substring(0, lastDotIndex);
    }

    public LocalDateTime getCreateTimeAsLocalDateTime() {
        if (createTime.matches("\\d+")) { 
            long epochMilli = Long.parseLong(createTime);
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
        } else {
            return LocalDateTime.parse(createTime, FORMATTER);
        }
    }

}
