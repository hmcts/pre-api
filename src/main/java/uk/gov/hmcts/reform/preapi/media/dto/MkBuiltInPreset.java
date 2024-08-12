package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class MkBuiltInPreset {

    public static final String BUILT_IN_PRESET_ASSET_CONVERTER = "#Microsoft.Media.BuiltInAssetConverterPreset";
    public static final String BUILT_IN_PRESET_STANDARD_ENCODER = "#Microsoft.Media.BuiltInStandardEncoderPreset";

    @JsonProperty("@odata.type")
    private String odataType;
    private MkAssetConverterPreset presetName;

    public enum MkAssetConverterPreset {
        CopyTopBitrateInterleaved,
        CopyAllBitrateNonInterleaved,
        CopyAllBitrateInterleaved,
        H264SingleBitrate720p
    }
}
