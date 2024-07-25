package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "@odata.type"
)
@JsonTypeName("#Microsoft.Media.BuiltInAssetConverterPreset")
public class MkBuiltInAssetConverterPreset {
    private MkAssetConverterPreset presetName;

    public enum MkAssetConverterPreset {
        CopyTopBitrateInterleaved,
        CopyAllBitrateNonInterleaved,
        CopyAllBitrateInterleaved,
        H264SingleBitrate720p
    }
}
