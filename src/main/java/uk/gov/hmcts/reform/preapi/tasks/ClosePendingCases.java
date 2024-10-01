package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.services.CaseService;

@Component
@Slf4j
public class ClosePendingCases implements Runnable {

    private final CaseService caseService;

    @Autowired
    public ClosePendingCases(CaseService caseService) {
        this.caseService = caseService;
    }

    @Override
    public void run() {
        log.info("Running ClosePendingCases task");

        try {
            caseService.closePendingCases();
            log.info("Successfully closed pending cases");
        } catch (Exception e) {
            log.error("Failed to close pending cases", e);
        }

        log.info("Completed ClosePendingCases task");
    }
}
