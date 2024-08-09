package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ClosePendingCasesTest {

    private CaseService caseService;
    private ClosePendingCases closePendingCasesTask;

    @BeforeEach
    public void setUp() {
        caseService = mock(CaseService.class);
        closePendingCasesTask = new ClosePendingCases(caseService);
    }

    @Test
    public void testRun() {
        closePendingCasesTask.run();

        verify(caseService, times(1)).closePendingCases();
    }
}
