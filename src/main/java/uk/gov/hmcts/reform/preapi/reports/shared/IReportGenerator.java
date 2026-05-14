package uk.gov.hmcts.reform.preapi.reports.shared;

import java.util.Optional;

@FunctionalInterface
public interface IReportGenerator {

    Optional<String> generateCsvReport();
}
