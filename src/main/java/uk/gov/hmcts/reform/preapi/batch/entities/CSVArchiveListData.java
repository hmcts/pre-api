package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
public class CSVArchiveListData implements IArchiveData  {
    private static final List<String> DATE_PATTERNS = List.of(
        "dd/MM/yyyy HH:mm", "dd/MM/yyyy H:mm",
        "d/MM/yyyy HH:mm", "d/MM/yyyy H:mm",
        "dd/M/yyyy HH:mm", "dd/M/yyyy H:mm",
        "d/M/yyyy HH:mm", "d/M/yyyy H:mm",
        "dd-MM-yyyy HH:mm", "dd-MM-yyyy H:mm",
        "d-MM-yyyy HH:mm", "d-MM-yyyy H:mm",
        "dd-M-yyyy HH:mm", "dd-M-yyyy H:mm",
        "d-M-yyyy HH:mm", "d-M-yyyy H:mm",
        "yyyy-MM-dd HH:mm:ss"
    );

    private String archiveName = "";
    private String sanitizedArchiveName = "";
    private String createTime = "";
    private Integer duration = 0;
    private String fileName = "";
    private String fileSize = "";

    public CSVArchiveListData(String archiveName, String createTime, Integer duration,
        String fileName, String fileSize) {
        this.archiveName = archiveName;
        this.createTime = createTime;
        this.duration = (duration != null) ? duration : 0;
        this.fileName = fileName;
        this.fileSize = fileSize;
    }

    public void setArchiveName(String archiveName) {
        this.archiveName = archiveName;
        this.sanitizedArchiveName = computeSanitizedName(archiveName);
    }


    private String computeSanitizedName(String archiveName) {
        if (archiveName == null || archiveName.isEmpty()) {
            return "";
        }

        String sanitized = archiveName
            .replaceAll("[-_\\s]?(?:CP-Case|AS URN)[-_\\s]?$", "")
            .replaceAll("_(?=\\.[^.]+$)", "")
            .replaceAll("[-_\\s]{2,}", "-")
            .replaceAll("CP_", "")
            .trim();

        return sanitized;
    }

    public void setDuration(Integer duration) {
        this.duration = (duration != null) ? duration : 0;
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

        } catch (NumberFormatException e) {
            return DATE_PATTERNS.stream()
                .map(pattern -> {
                    try {
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.UK);
                        return LocalDateTime.parse(createTime.trim(), formatter);
                    } catch (DateTimeParseException ex) {
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        }
    }

    @Override
    public String toString() {
        return "CSVArchiveListData{"
               + "archiveName='" + archiveName + '\''
               + ", sanitizedName='" + sanitizedArchiveName + '\''
               + ", createTime='" + createTime + '\''
               + ", duration=" + duration
               + ", fileName='" + fileName + '\''
               + ", fileSize='" + fileSize + '\''
               + '}';
    }
}
