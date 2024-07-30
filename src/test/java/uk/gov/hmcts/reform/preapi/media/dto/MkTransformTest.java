package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static uk.gov.hmcts.reform.preapi.media.MediaKind.ENCODE_FROM_INGEST_TRANSFORM;
import static uk.gov.hmcts.reform.preapi.media.MediaKind.ENCODE_FROM_MP4_TRANSFORM;

public class MkTransformTest {

    @Test
    @SuppressWarnings("checkstyle:LineLength")
    void testMkTransform() throws JsonProcessingException {
        var o = MkTransform.builder()
                           .properties(
                               MkTransformProperties.builder()
                                    .outputs(List.of(
                                        MkTransformOutput.builder()
                                                         .relativePriority(MkTransformOutput.MkTransformPriority.Normal)
                                                         .preset(getMkBuiltInPreset(ENCODE_FROM_INGEST_TRANSFORM))
                                                         .build()
                                    ))
                                    .build()
                           )
                           .build();
        var om = new ObjectMapper();
        Assertions.assertEquals("{\"name\":null,\"properties\":{\"description\":null,\"outputs\":[{\"preset\":{\"@odata.type\":\"#Microsoft.Media.BuiltInAssetConverterPreset\",\"presetName\":\"CopyAllBitrateNonInterleaved\"},\"relativePriority\":\"Normal\"}]}}", om.writeValueAsString(o));

        var o2 = MkTransform.builder()
                           .properties(
                               MkTransformProperties.builder()
                                    .outputs(List.of(
                                        MkTransformOutput.builder()
                                                         .relativePriority(MkTransformOutput.MkTransformPriority.Normal)
                                                         .preset(getMkBuiltInPreset(ENCODE_FROM_MP4_TRANSFORM))
                                                         .build()
                                    ))
                                    .build()
                           )
                           .build();
        Assertions.assertEquals("{\"name\":null,\"properties\":{\"description\":null,\"outputs\":[{\"preset\":{\"@odata.type\":\"#Microsoft.Media.BuiltInStandardEncoderPreset\",\"presetName\":\"H264SingleBitrate720p\"},\"relativePriority\":\"Normal\"}]}}", om.writeValueAsString(o2));
    }

    private MkBuiltInPreset getMkBuiltInPreset(String transformName) {
        return switch(transformName) {
            case ENCODE_FROM_INGEST_TRANSFORM -> MkBuiltInPreset
                .builder()
                .odataType(MkBuiltInPreset.BUILT_IN_PRESET_ASSET_CONVERTER)
                .presetName(MkBuiltInPreset.MkAssetConverterPreset.CopyAllBitrateNonInterleaved)
                .build();
            case ENCODE_FROM_MP4_TRANSFORM -> MkBuiltInPreset
                .builder()
                .odataType(MkBuiltInPreset.BUILT_IN_PRESET_STANDARD_ENCODER)
                .presetName(MkBuiltInPreset.MkAssetConverterPreset.H264SingleBitrate720p)
                .build();
            default -> throw new IllegalArgumentException(
                "Invalid MediaKind transform name '" + transformName + "'"
            );
        };
    }
}
