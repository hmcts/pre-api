package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = { ReferenceDataProcessor.class })
public class ReferenceDataProcessorTest {
    @MockitoBean
    private InMemoryCacheService cacheService;

    @MockitoBean
    private LoggingService loggingService;

    @Autowired
    private ReferenceDataProcessor referenceDataProcessor;

    @Test
    @DisplayName("Should log error on invalid object passed to processor")
    void processInvalidType() {
        String test = "test";

        referenceDataProcessor.process(test);

        verify(loggingService, times(1)).logError("Unsupported reference data type: %s", String.class.getName());
    }

    @Test
    @DisplayName("Should log error processing failure")
    void processProcessingFailure() {
        CSVSitesData csvSitesData = new CSVSitesData();
        csvSitesData.setSiteReference("siteReference");
        csvSitesData.setCourtName("courtName");
        RuntimeException error = new RuntimeException("error");
        doThrow(error).when(cacheService).saveSiteReference(any(), any());

        referenceDataProcessor.process(csvSitesData);

        verify(cacheService, times(1)).saveSiteReference(csvSitesData.getSiteReference(), csvSitesData.getCourtName());
        verify(loggingService, times(1)).logError(eq("Error processing reference data: %s - %s"), anyString(), any());
    }

    @Test
    @DisplayName("Should process CSVSitesData object and save to cache service")
    void processCSVSitesData() {
        CSVSitesData csvSitesData = new CSVSitesData();
        csvSitesData.setSiteReference("siteReference");
        csvSitesData.setCourtName("courtName");

        referenceDataProcessor.process(csvSitesData);

        verify(cacheService, times(1)).saveSiteReference(csvSitesData.getSiteReference(), csvSitesData.getCourtName());
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("Should process CSVChannelData object and save to cache service")
    void processCSVChannelData() {
        CSVChannelData csvChannelData = new CSVChannelData();
        csvChannelData.setChannelName("channelName");
        csvChannelData.setChannelUser("channelUser");
        csvChannelData.setChannelUserEmail("channelUserEmail");
        when(cacheService.getChannelReference(csvChannelData.getChannelName()))
            .thenReturn(Optional.empty());

        referenceDataProcessor.process(csvChannelData);

        verify(cacheService, times(1))
            .getChannelReference(eq(csvChannelData.getChannelName()));
        verify(cacheService, times(1))
            .saveChannelReference(eq(csvChannelData.getChannelName()), any(List.class));
    }
}
