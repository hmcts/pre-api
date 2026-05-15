package uk.gov.hmcts.reform.preapi.tasks.reports;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.reports.UserFullAccessCsvReportGenerator;

@Slf4j
@Component
public class ReportUserFullAccess implements Runnable {

    private final UserFullAccessCsvReportGenerator csvReportGenerator;

    @Autowired
    public ReportUserFullAccess(UserFullAccessCsvReportGenerator csvReportGenerator) {
        this.csvReportGenerator = csvReportGenerator;
    }

    @Override
    public void run() {
        log.info("Starting ReportUserFullAccess Task");
        csvReportGenerator.generateCsvReport();
        // TODO: Upload the output to Sharepoint file
    }





}
