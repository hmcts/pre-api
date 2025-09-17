package uk.gov.hmcts.reform.preapi.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.services.CaseService;

@TestConfiguration
public class RollbackTestConfig {

    // Fake CaseService that marks the CURRENT (outer step) tx as rollback-only
    @Bean
    @Primary
    public CaseService rollbackOnlyCaseService() {
        return new CaseService(null, null, null, null, null, null, null, null, false) {
            @Override
            public UpsertResult upsert(CreateCaseDTO caseData) {
                // We don't throw; we just poison the tx
                org.springframework.transaction.interceptor.TransactionAspectSupport
                    .currentTransactionStatus()
                    .setRollbackOnly();
                return null;
            }
            // stub other methods if your interface has more
        };
    }
}
