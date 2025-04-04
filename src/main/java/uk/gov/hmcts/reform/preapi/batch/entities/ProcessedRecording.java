package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
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
    private String courtReference;
    private String courtName;
    private Court court;
    private CaseState state;
    private Timestamp recordingTimestamp;
    private String urn;
    private String exhibitReference;
    private String caseReference;
    private String defendantLastName;
    private String witnessFirstName;
    private String recordingVersion;
    private String recordingVersionNumberStr;
    private int recordingVersionNumber;
    private Duration duration;
    private boolean isMostRecentVersion;
    private String fileExtension;
    private String fileName;
    private List<Map<String, String>> shareBookingContacts;

    public String getFullCourtName() {
        return courtName;
    }

    // TODO remove unused ?
    public boolean isOrig() {
        return Constants.VALID_ORIG_TYPES.contains(this.getRecordingVersion().toUpperCase());
    }

    // TODO remove unused ?
    public boolean isCopy() {
        return Constants.VALID_COPY_TYPES.contains(this.getRecordingVersion().toUpperCase());
    }

    // TODO remove unused ?
    public boolean isRecordingType(String type) {
        return type.equalsIgnoreCase(this.recordingVersion);
    }

    public Timestamp getFinishedAt() {
        if (recordingTimestamp == null || duration == null) {
            return null;
        }
        Instant finishedAt = recordingTimestamp.toInstant().plus(duration);
        return Timestamp.from(finishedAt);
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
            .add("recordingVersion='" + recordingVersion + "'")
            .add("caseReference='" + caseReference + "'")
            .add("recordingVersionNumberStr=" + recordingVersionNumberStr)
            .add("recordingVersionNumber=" + recordingVersionNumber)
            .add("duration=" + duration)
            .add("isMostRecentVersion=" + isMostRecentVersion)
            .add("fileExtension='" + fileExtension + "'")
            .add("fileName='" + fileName + "'")
            .add("shareBookingContacts=" + shareBookingContacts)
            .toString();
    }
}

