package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
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
@AllArgsConstructor
public class CSVExemptionListData {
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
    
    private String archiveName;
    private String createTime;
    private int duration;
    private String courtReference;
    private String urn;
    private String exhibitReference;
    private String defendantName;
    private String witnessName;
    private String recordingVersion;
    private int recordingVersionNumber;
    private String fileExtension;
    private String fileName;
    private String fileSize;
    private String reason;
    private String addedBy;

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
        return "CSVExemptionListData{"
                + "archiveName='" + archiveName + '\''
                + ", createTime='" + createTime + '\''
                + ", duration=" + duration
                + ", courtReference='" + courtReference + '\''
                + ", urn='" + urn + '\''
                + ", exhibitReference='" + exhibitReference + '\''
                + ", defendantName='" + defendantName + '\''
                + ", witnessName='" + witnessName + '\''
                + ", recordingVersion='" + recordingVersion + '\''
                + ", recordingVersionNumber=" + recordingVersionNumber
                + ", reason='" + reason + '\''
                + ", addedBy='" + addedBy + '\''
                + ", fileExtension='" + fileExtension + '\''
                + ", fileName='" + fileName + '\''
                + ", fileSize='" + fileSize + '\''
                + '}';
    }
}
