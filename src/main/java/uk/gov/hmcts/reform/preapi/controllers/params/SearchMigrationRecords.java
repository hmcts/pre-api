package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;

import java.time.LocalDate;
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

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createDateFrom;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate createDateTo;
}
