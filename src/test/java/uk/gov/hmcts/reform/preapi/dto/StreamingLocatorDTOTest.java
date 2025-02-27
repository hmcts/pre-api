package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorDTO;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorPropertiesDTO;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocator;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;

import java.sql.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StreamingLocatorDTOTest {

    private MkStreamingLocator mkStreamingLocator;
    private StreamingLocatorPropertiesDTO propertiesDTO;

    @BeforeEach
    void setUp() {
        mkStreamingLocator = mock(MkStreamingLocator.class);
        propertiesDTO = mock(StreamingLocatorPropertiesDTO.class);
        MkStreamingLocatorProperties mkStreamingLocatorProperties = mock(MkStreamingLocatorProperties.class);

        when(mkStreamingLocator.getName()).thenReturn("TestName");
        when(mkStreamingLocator.getAssetName()).thenReturn("TestAsset");
        when(mkStreamingLocator.getStreamingLocatorId()).thenReturn("12345");
        when(mkStreamingLocator.getStreamingPolicyName()).thenReturn("PolicyName");
        when(mkStreamingLocator.getCreated()).thenReturn(new Date(1000L));
        when(mkStreamingLocator.getEndTime()).thenReturn(new Date(2000L));
        when(mkStreamingLocator.getProperties()).thenReturn(mkStreamingLocatorProperties);
        when(mkStreamingLocatorProperties.getAlternativeMediaId()).thenReturn("AltMediaId");
    }

    @Test
    void testNoArgsConstructor() {
        StreamingLocatorDTO streamingLocatorDTO = new StreamingLocatorDTO();
        assertNotNull(streamingLocatorDTO);
    }

    @Test
    void testAllArgsConstructor() {
        Date created = new Date(1000L);
        Date endTime = new Date(2000L);
        StreamingLocatorDTO streamingLocatorDTO = new StreamingLocatorDTO("TestName",
                "TestAsset", "12345",
                "PolicyName", created, endTime, propertiesDTO);

        assertEquals("TestName", streamingLocatorDTO.getName());
        assertEquals("TestAsset", streamingLocatorDTO.getAssetName());
        assertEquals("12345", streamingLocatorDTO.getStreamingLocatorId());
        assertEquals("PolicyName", streamingLocatorDTO.getStreamingPolicyName());
        assertEquals(created, streamingLocatorDTO.getCreated());
        assertEquals(endTime, streamingLocatorDTO.getEndTime());
        assertEquals(propertiesDTO, streamingLocatorDTO.getProperties());
    }

    @Test
    void testMkStreamingLocatorConstructor() {
        StreamingLocatorDTO streamingLocatorDTO = new StreamingLocatorDTO(mkStreamingLocator);

        assertEquals("TestName", streamingLocatorDTO.getName());
        assertEquals("TestAsset", streamingLocatorDTO.getAssetName());
        assertEquals("12345", streamingLocatorDTO.getStreamingLocatorId());
        assertEquals("PolicyName", streamingLocatorDTO.getStreamingPolicyName());
        assertEquals(new Date(1000L), streamingLocatorDTO.getCreated());
        assertEquals(new Date(2000L), streamingLocatorDTO.getEndTime());
        assertNotNull(streamingLocatorDTO.getProperties());
    }

    @Test
    void testSetters() {
        StreamingLocatorDTO streamingLocatorDTO = new StreamingLocatorDTO();
        Date created = new Date(System.currentTimeMillis());
        Date endTime = new Date(System.currentTimeMillis());

        streamingLocatorDTO.setName("NewName");
        streamingLocatorDTO.setAssetName("NewAsset");
        streamingLocatorDTO.setStreamingLocatorId("67890");
        streamingLocatorDTO.setStreamingPolicyName("NewPolicy");
        streamingLocatorDTO.setCreated(created);
        streamingLocatorDTO.setEndTime(endTime);
        streamingLocatorDTO.setProperties(propertiesDTO);

        assertEquals("NewName", streamingLocatorDTO.getName());
        assertEquals("NewAsset", streamingLocatorDTO.getAssetName());
        assertEquals("67890", streamingLocatorDTO.getStreamingLocatorId());
        assertEquals("NewPolicy", streamingLocatorDTO.getStreamingPolicyName());
        assertEquals(created, streamingLocatorDTO.getCreated());
        assertEquals(endTime, streamingLocatorDTO.getEndTime());
        assertEquals(propertiesDTO, streamingLocatorDTO.getProperties());
    }
}



