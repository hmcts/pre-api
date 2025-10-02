package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfFailureReason;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
public class SearchMigrationRecords {
    private String caseReference;
    private String witnessName;
    private String defendantName;
    private UUID courtId;
    private String courtReference;
    private VfMigrationStatus status;
    private List<VfFailureReason> reasonIn;
    private List<VfFailureReason> reasonNotIn;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createDateFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createDateTo;

    public Timestamp getCreateDateFromTimestamp() {
        return createDateFrom != null ? Timestamp.from(createDateFrom.toInstant()) : null;
    }

    public Timestamp getCreateDateToTimestamp() {
        return createDateTo != null ? Timestamp.from(createDateTo.toInstant().plusSeconds(86399)) : null;
    }

    public List<String> getReasonIn() {
        return reasonIn != null ? reasonIn.stream().map(VfFailureReason::toString).toList() : null;
    }

    public List<String> getReasonNotIn() {
        return reasonNotIn != null ? reasonNotIn.stream().map(VfFailureReason::toString).toList() : null;
    }
}
