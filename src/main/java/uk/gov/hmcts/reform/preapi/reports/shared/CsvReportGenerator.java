package uk.gov.hmcts.reform.preapi.reports.shared;

import com.opencsv.CSVWriter;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.utils.ListOfStringsCaseInsensitiveSorter;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Optional;

@NoArgsConstructor
@Slf4j
public class CsvReportGenerator {

    public <T> Optional<String> generateCsvReport(List<String> columnOrder,
                                                  List<T> writableObjects,
                                                  Class<T> reportClass) {
        if (writableObjects == null || writableObjects.isEmpty()) {
            log.info("No writable objects provided");
            return Optional.empty();
        }

        HeaderColumnNameMappingStrategy<T> mappingStrategy
            = new HeaderColumnNameMappingStrategy<>();
        mappingStrategy.setType(reportClass);
        mappingStrategy.setColumnOrderOnWrite(new ListOfStringsCaseInsensitiveSorter(columnOrder));

        try (StringWriter sw = new StringWriter(); CSVWriter csvWriter = new CSVWriter(sw)) {
            StatefulBeanToCsv<T> beanToCsv =
                new StatefulBeanToCsvBuilder<T>(csvWriter)
                    .withMappingStrategy(mappingStrategy)
                    .withApplyQuotesToAll(false)
                    .withOrderedResults(true)
                    .build();
            beanToCsv.write(writableObjects);
            return Optional.of(sw.toString());
        } catch (IOException | CsvRequiredFieldEmptyException | CsvDataTypeMismatchException e) {
            throw new BadRequestException("Unable to generate csv report: ", e);
        }
    }
}
