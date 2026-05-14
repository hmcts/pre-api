package uk.gov.hmcts.reform.preapi.reports;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.reports.shared.CsvReportGenerator;
import uk.gov.hmcts.reform.preapi.reports.shared.IReportGenerator;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.util.List;
import java.util.Optional;

@Service
public class UserFullAccessCsvReportGenerator extends CsvReportGenerator implements IReportGenerator {

    private final ReportService reportService;

    private static final List<String> COLUMN_ORDER = List.of(
        "First name", "Last name",
        "Primary email", "Additional email",
        "Court name", "Access role",
        "Access type", "Active"
    );

    public UserFullAccessCsvReportGenerator(ReportService reportService) {
        super();
        this.reportService = reportService;
    }

    @Override
    public Optional<String> generateCsvReport() {
        List<UserAccessReportDTO> userAccessReportDTOS = reportService.reportUserFullAccess();
        return generateCsvReport(COLUMN_ORDER, userAccessReportDTOS, UserAccessReportDTO.class);
    }

}

