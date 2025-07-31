package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVChannelData;
import uk.gov.hmcts.reform.preapi.batch.entities.CSVSitesData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
    void shouldCreateNewListWhenNoExistingChannelUsers() throws Exception {
        CSVChannelData channel = mock(CSVChannelData.class);
        when(channel.getChannelName()).thenReturn("caseRef|jane|roe");
        when(channel.getChannelUser()).thenReturn("Jane.Roe");
        when(channel.getChannelUserEmail()).thenReturn("jane.roe@example.com");

        when(cacheService.getChannelReference("caseRef|jane|roe")).thenReturn(Optional.empty());

        Object result = processor.process(channel);
        assertNull(result);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String[]>> listCaptor = ArgumentCaptor.forClass(List.class);
        verify(cacheService).saveChannelReference(eq("caseRef|jane|roe"), listCaptor.capture());

        List<String[]> saved = listCaptor.getValue();
        assertEquals(1, saved.size());
        assertEquals("Jane.Roe", saved.get(0)[0]);
        assertEquals("jane.roe@example.com", saved.get(0)[1]);
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

    @Test
    void shouldLogErrorWhenExceptionThrownDuringChannelGet() throws Exception {
        CSVChannelData channel = mock(CSVChannelData.class);
        when(channel.getChannelName()).thenReturn("key");
        when(channel.getChannelUser()).thenReturn("X.Y");
        when(channel.getChannelUserEmail()).thenReturn("x.y@example.com");

        when(cacheService.getChannelReference("key")).thenThrow(new RuntimeException("get-fail"));

        Object result = processor.process(channel);
        assertNull(result);
        verify(loggingService).logError(startsWith("Error processing reference data:"), any(), any());
        verify(cacheService, never()).saveChannelReference(anyString(), anyList());
    }

    @Test
    void shouldLogErrorWhenExceptionThrownDuringChannelSave() throws Exception {
        CSVChannelData channel = mock(CSVChannelData.class);
        when(channel.getChannelName()).thenReturn("key2");
        when(channel.getChannelUser()).thenReturn("Z.Q");
        when(channel.getChannelUserEmail()).thenReturn("z.q@example.com");

        when(cacheService.getChannelReference("key2")).thenReturn(Optional.of(new ArrayList<>()));
        doThrow(new RuntimeException("save-fail"))
            .when(cacheService).saveChannelReference(eq("key2"), anyList());

        Object result = processor.process(channel);
        assertNull(result);
        verify(loggingService).logError(startsWith("Error processing reference data:"), any(), any());
    }

    @Test
    void shouldLogErrorForUnsupportedReferenceType() throws Exception {
        Object unknown = Integer.valueOf(42);
        Object result = processor.process(unknown);

        assertNull(result);

        verify(loggingService).logError(
            eq("Unsupported reference data type: %s"),
            eq("java.lang.Integer")
        );
    }
}