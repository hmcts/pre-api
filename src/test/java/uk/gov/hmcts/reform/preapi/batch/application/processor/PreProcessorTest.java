package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.createCase;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.createCourt;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.createDefaultTestUser;

@SpringBootTest(classes = PreProcessor.class)
class PreProcessorTest {

    @MockitoBean
    private LoggingService loggingService;

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private UserRepository userRepository;

    @Autowired
    private PreProcessor preProcessor;

    @Test
    void shouldInitializeAndCacheEntities() {
        var court = createCourt(CourtType.CROWN, "Test Court", "123");
        court.setId(UUID.randomUUID());
        var testCase = createCase(court, "1234567890", false, null);
        testCase.setId(UUID.randomUUID());
        var user = createDefaultTestUser();
        user.setId(UUID.randomUUID());
        when(courtRepository.findAll()).thenReturn(Collections.singletonList(court));
        when(caseRepository.findAll()).thenReturn(Collections.singletonList(testCase));
        when(userRepository.findAll()).thenReturn(Collections.singletonList(user));

        preProcessor.initialize();

        verify(loggingService).logInfo("Initiating batch environment preparation.");
        verify(cacheService).clearNamespaceKeys(anyString());
        verify(cacheService, times(1)).saveCourt(anyString(), any(CourtDTO.class));
        verify(cacheService, times(1)).saveCase(anyString(), any(CreateCaseDTO.class));
        verify(cacheService, times(1)).saveHashAll(anyString(), anyMap());
        verify(loggingService).logInfo("Batch environment preparation completed successfully.");
    }

    @Test
    void shouldLogErrorWhenInitializationFails() {
        doThrow(new RuntimeException("Test Exception")).when(cacheService).clearNamespaceKeys(anyString());

        try {
            preProcessor.initialize();
        } catch (RuntimeException e) {
            // Expected exception
        }

        verify(loggingService).logError(eq("Batch environment preparation failed: %s"), any(RuntimeException.class));
    }
}
