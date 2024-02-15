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
    private Date scheduledFor;
    private UUID courtId;
    private String witnessName;
    private String defendantName;
    private Boolean includeDeleted;

    @Nullable
    private Timestamp scheduledForFrom;
    @Nullable
    private Timestamp scheduledForUntil;
    private List<UUID> authorisedBookings;
    private UUID authorisedCourt;
}
