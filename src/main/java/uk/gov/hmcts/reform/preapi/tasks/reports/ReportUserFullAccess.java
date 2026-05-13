package uk.gov.hmcts.reform.preapi.tasks.reports;

import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.reports.ColumnOrderComparator;
import uk.gov.hmcts.reform.preapi.reports.UserFullAccessCsvReportGenerator;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
        csvReportGenerator.generateCsvReport("temp/reports/PREUsersFullAccessReport.csv");

        // TODO: Upload to sharepoint

    }





}
