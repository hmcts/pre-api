package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.media.StreamingLocatorPropertiesDTO;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties;
import uk.gov.hmcts.reform.preapi.media.dto.MkStreamingLocatorProperties.MkContentKey;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StreamingLocatorPropertiesDTOTest {

    private MkStreamingLocatorProperties mkStreamingLocatorProperties;

    @BeforeEach
    public void setUp() {
        mkStreamingLocatorProperties = mock(MkStreamingLocatorProperties.class);
        MkContentKey mkContentKey = mock(MkContentKey.class);

        when(mkStreamingLocatorProperties.getAlternativeMediaId()).thenReturn("AltMediaId");
        when(mkStreamingLocatorProperties.getAssetName()).thenReturn("AssetName");
        when(mkStreamingLocatorProperties.getDefaultContentKeyPolicyName()).thenReturn("PolicyName");
        when(mkStreamingLocatorProperties.getEndTime()).thenReturn(new Timestamp(2000L));
        when(mkStreamingLocatorProperties.getFilters()).thenReturn(Arrays.asList("Filter1", "Filter2"));
        when(mkStreamingLocatorProperties.getStartTime()).thenReturn(new Timestamp(1000L));
        when(mkStreamingLocatorProperties.getStreamingLocatorId()).thenReturn("12345");
        when(mkStreamingLocatorProperties.getStreamingPolicyName()).thenReturn("StreamingPolicy");
        when(mkStreamingLocatorProperties.getContentKeys()).thenReturn(Collections.singletonList(mkContentKey));

        when(mkContentKey.getId()).thenReturn("KeyId");
        when(mkContentKey.getLabelReferenceInStreamingPolicy()).thenReturn("LabelRef");
        when(mkContentKey.getPolicyName()).thenReturn("PolicyName");
        when(mkContentKey.getType()).thenReturn("Type");
        when(mkContentKey.getValue()).thenReturn("Value");
    }

    @Test
    public void testNoArgsConstructor() {
        StreamingLocatorPropertiesDTO propertiesDTO = new StreamingLocatorPropertiesDTO();
        assertNotNull(propertiesDTO);
    }

    @Test
    public void testAllArgsConstructor() {
        Timestamp startTime = new Timestamp(1000L);
        Timestamp endTime = new Timestamp(2000L);
        List<String> filters = Arrays.asList("Filter1", "Filter2");
        StreamingLocatorPropertiesDTO.ContentKey contentKey = new StreamingLocatorPropertiesDTO.ContentKey(
                "KeyId", "LabelRef",
                "PolicyName", "Type", "Value");
        List<StreamingLocatorPropertiesDTO.ContentKey> contentKeys = Collections.singletonList(contentKey);
        StreamingLocatorPropertiesDTO propertiesDTO = new StreamingLocatorPropertiesDTO(
                "AltMediaId", "AssetName", contentKeys,
                "PolicyName", endTime, filters, startTime,
                "12345", "StreamingPolicy");

        assertEquals("AltMediaId", propertiesDTO.getAlternativeMediaId());
        assertEquals("AssetName", propertiesDTO.getAssetName());
        assertEquals("PolicyName", propertiesDTO.getDefaultContentKeyPolicyName());
        assertEquals(endTime, propertiesDTO.getEndTime());
        assertEquals(filters, propertiesDTO.getFilters());
        assertEquals(startTime, propertiesDTO.getStartTime());
        assertEquals("12345", propertiesDTO.getStreamingLocatorId());
        assertEquals("StreamingPolicy", propertiesDTO.getStreamingPolicyName());
        assertEquals(contentKeys, propertiesDTO.getContentKeys());
    }

    @Test
    public void testMkStreamingLocatorPropertiesConstructor() {
        StreamingLocatorPropertiesDTO propertiesDTO = new StreamingLocatorPropertiesDTO(mkStreamingLocatorProperties);

        assertEquals("AltMediaId", propertiesDTO.getAlternativeMediaId());
        assertEquals("AssetName", propertiesDTO.getAssetName());
        assertEquals("PolicyName", propertiesDTO.getDefaultContentKeyPolicyName());
        assertEquals(new Timestamp(2000L), propertiesDTO.getEndTime());
        assertEquals(Arrays.asList("Filter1", "Filter2"), propertiesDTO.getFilters());
        assertEquals(new Timestamp(1000L), propertiesDTO.getStartTime());
        assertEquals("12345", propertiesDTO.getStreamingLocatorId());
        assertEquals("StreamingPolicy", propertiesDTO.getStreamingPolicyName());
        assertNotNull(propertiesDTO.getContentKeys());
        assertEquals(1, propertiesDTO.getContentKeys().size());
    }

    @Test
    public void testSetters() {
        StreamingLocatorPropertiesDTO propertiesDTO = new StreamingLocatorPropertiesDTO();
        Timestamp startTime = new Timestamp(System.currentTimeMillis());
        Timestamp endTime = new Timestamp(System.currentTimeMillis());
        List<String> filters = Arrays.asList("Filter1", "Filter2");
        StreamingLocatorPropertiesDTO.ContentKey contentKey = new StreamingLocatorPropertiesDTO.ContentKey(
                "KeyId", "LabelRef",
                "PolicyName", "Type", "Value");
        List<StreamingLocatorPropertiesDTO.ContentKey> contentKeys = Collections.singletonList(contentKey);

        propertiesDTO.setAlternativeMediaId("AltMediaId");
        propertiesDTO.setAssetName("AssetName");
        propertiesDTO.setContentKeys(contentKeys);
        propertiesDTO.setDefaultContentKeyPolicyName("PolicyName");
        propertiesDTO.setEndTime(endTime);
        propertiesDTO.setFilters(filters);
        propertiesDTO.setStartTime(startTime);
        propertiesDTO.setStreamingLocatorId("12345");
        propertiesDTO.setStreamingPolicyName("StreamingPolicy");

        assertEquals("AltMediaId", propertiesDTO.getAlternativeMediaId());
        assertEquals("AssetName", propertiesDTO.getAssetName());
        assertEquals(contentKeys, propertiesDTO.getContentKeys());
        assertEquals("PolicyName", propertiesDTO.getDefaultContentKeyPolicyName());
        assertEquals(endTime, propertiesDTO.getEndTime());
        assertEquals(filters, propertiesDTO.getFilters());
        assertEquals(startTime, propertiesDTO.getStartTime());
        assertEquals("12345", propertiesDTO.getStreamingLocatorId());
        assertEquals("StreamingPolicy", propertiesDTO.getStreamingPolicyName());
    }
}
