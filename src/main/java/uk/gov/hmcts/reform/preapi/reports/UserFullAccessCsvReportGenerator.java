package uk.gov.hmcts.reform.preapi.reports;

import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.reports.UserAccessReportDTO;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

@Service
public class UserFullAccessCsvReportGenerator {

    private final ReportService reportService;

    private static final List<String> COLUMN_ORDER = List.of(
        "First name", "Last name",
        "Primary email", "Additional email",
        "Court name", "Access role",
        "Access type", "Active"
    );

    public UserFullAccessCsvReportGenerator(ReportService reportService) {
        this.reportService = reportService;
    }

    public void generateCsvReport(String outputFilename) {
        List<UserAccessReportDTO> userAccessReportDTOS = reportService.reportUserFullAccess();

        HeaderColumnNameMappingStrategy<UserAccessReportDTO> mappingStrategy
            = new HeaderColumnNameMappingStrategy<>();
        mappingStrategy.setType(UserAccessReportDTO.class);
        mappingStrategy.setColumnOrderOnWrite(new ColumnOrderComparator(COLUMN_ORDER));

        try (Writer writer = new FileWriter(outputFilename)) {
            StatefulBeanToCsv<UserAccessReportDTO> beanToCsv =
                new StatefulBeanToCsvBuilder<UserAccessReportDTO>(writer)
                    .withMappingStrategy(mappingStrategy)
                    .withApplyQuotesToAll(false)
                    .withOrderedResults(true)
                    .build();
            beanToCsv.write(userAccessReportDTOS);
            writer.close();

            System.out.println("Report written to CSV at " + outputFilename);
        } catch (IOException | CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new RuntimeException(e);
        }
    }

}
