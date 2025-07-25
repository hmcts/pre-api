package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;

import java.util.Date;
import java.util.UUID;

@Data
@NoArgsConstructor
public class SearchMigrationRecords {
    private String caseReference;

    private String witnessName;

    private String defendantName;

    private UUID courtId;

    private VfMigrationStatus status;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createDateFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date createDateTo;
}
