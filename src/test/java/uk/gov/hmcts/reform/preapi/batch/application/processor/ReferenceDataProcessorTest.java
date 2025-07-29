package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.startsWith;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ReferenceDataProcessor.class)
class ReferenceDataProcessorTest {

    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private ReferenceDataProcessor processor;

    @Test
    void shouldCacheSiteReference() throws Exception {
        CSVSitesData sites = mock(CSVSitesData.class);
        when(sites.getSiteReference()).thenReturn("SITE1");
        when(sites.getCourtName()).thenReturn("Court A");

        Object result = processor.process(sites);

        assertNull(result);
        verify(cacheService).saveSiteReference("SITE1", "Court A");
    }


    @Test
    void shouldLogErrorWhenExceptionThrownDuringSitesProcessing() throws Exception {
        CSVSitesData sites = mock(CSVSitesData.class);
        when(sites.getSiteReference()).thenReturn("SITE2");
        when(sites.getCourtName()).thenReturn("Court B");

        doThrow(new RuntimeException("boom")).when(cacheService).saveSiteReference("SITE2", "Court B");

        Object result = processor.process(sites);

        assertNull(result);
        verify(loggingService).logError(startsWith("Error processing reference data:"), any(), any());
    }
}