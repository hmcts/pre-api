package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

@Data
public class SearchRecordings {
    private UUID captureSessionId;
    private UUID parentRecordingId;
    private UUID participantId;
    private String caseReference;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startedAt;
    private UUID courtId;
    private String witnessName;
    private String defendantName;
    private Boolean includeDeleted;
    private String id;

    @Nullable
    private Timestamp startedAtFrom;
    @Nullable
    private Timestamp startedAtUntil;
    private List<UUID> authorisedBookings;
    private UUID authorisedCourt;
    private Boolean caseOpen;

    public String getCaseReference() {
        return caseReference != null && !caseReference.isEmpty() ? caseReference : null;
    }

    public String getWitnessName() {
        return witnessName != null && !witnessName.isEmpty() ? witnessName : null;
    }

    public String getDefendantName() {
        return defendantName != null && !defendantName.isEmpty() ? defendantName : null;
    }

    public String getId() {
        return id != null && !id.isEmpty() ? id : null;
    }

    // used in recordingRepository.searchAllBy()
    public boolean isCaseOpen() {
        return caseOpen == null || caseOpen;
    }
}
