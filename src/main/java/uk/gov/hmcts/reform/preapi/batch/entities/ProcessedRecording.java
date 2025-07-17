package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedRecording {
    private String archiveId;
    private String archiveName;
    
    // Identifiers and court metadata
    private String courtReference;          // reference for the court
    private String courtName;               // Human-readable court name
    private Court court;                    // Linked Court entity 

    private CaseState state;                // Current state of the case (e.g. OPEN, CLOSED)

    // Recording metadata
    private Timestamp recordingTimestamp;   // When the recording began
    private Duration duration;              // How long the recording lasted

    // Case and participant data
    private String urn;                     // Unique reference number for the case
    private String exhibitReference;        // Another reference related to court
    private String caseReference;           // Derived case reference for migration
    private String defendantLastName;
    private String witnessFirstName;

    // Extracted versioning info (from filename, e.g. ORIG2)
    private String extractedRecordingVersion;               // Raw extracted version label (e.g. "ORIG", "COPY")
    private String extractedRecordingVersionNumberStr;      // Raw version number string from filename (e.g. "2")
    private String origVersionNumberStr; // e.g. "2" if ORIG2, null otherwise
    private String copyVersionNumberStr; // e.g. "1.2" if COPY1.2, null otherwise
    private int recordingVersionNumber;                     // Parsed version number (1 = ORIG, 2 = COPY)
    
    private boolean isMostRecentVersion; // (used to skip older recordings)
    
    private String fileExtension;                           // e.g. ".mp4", ".raw"
    private String fileName;                                // Full filename of the recording

    // List of channel share booking contacts linked to this recording
    private List<Map<String, String>> shareBookingContacts;

    public String getFullCourtName() {
        return courtName;
    }

    public Timestamp getFinishedAt() {
        if (recordingTimestamp == null || duration == null) {
            return null;
        }
        Instant finishedAt = recordingTimestamp.toInstant().plus(duration);
        return Timestamp.from(finishedAt);
    }

    public String getFullVersionString() {
        return recordingVersionNumber == 1
            ? origVersionNumberStr
            : origVersionNumberStr + "." + copyVersionNumberStr;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", ProcessedRecording.class.getSimpleName() + "{", "}")
            .add("courtReference='" + courtReference + "'")
            .add("courtName='" + courtName + "'")
            .add("court=" + court)
            .add("state=" + state)
            .add("recordingTimestamp=" + recordingTimestamp)
            .add("urn='" + urn + "'")
            .add("exhibitReference='" + exhibitReference + "'")
            .add("defendantLastName='" + defendantLastName + "'")
            .add("witnessFirstName='" + witnessFirstName + "'")
            .add("extractedRecordingVersion='" + extractedRecordingVersion + "'")
            .add("extractedRecordingVersionNumberStr=" + extractedRecordingVersionNumberStr)
            .add("origVersionNumberStr=" + origVersionNumberStr)
            .add("copyVersionNumberStr=" + copyVersionNumberStr)
            .add("recordingVersionNumber=" + recordingVersionNumber)
            .add("duration=" + duration)
            .add("isMostRecentVersion=" + isMostRecentVersion)
            .add("fileExtension='" + fileExtension + "'")
            .add("fileName='" + fileName + "'")
            .add("shareBookingContacts=" + shareBookingContacts)
            .toString();
    }
}

