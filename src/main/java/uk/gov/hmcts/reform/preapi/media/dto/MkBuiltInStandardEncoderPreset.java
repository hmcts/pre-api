package uk.gov.hmcts.reform.preapi.media.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Builder
@Data
@NoArgsConstructor
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    property = "@odata.type"
)
@JsonTypeName("#Microsoft.Media.BuiltInStandardEncoderPreset")
public class MkBuiltInStandardEncoderPreset implements IMkBuiltInPreset {
    protected final MkAssetConverterPreset presetName = MkAssetConverterPreset.H264SingleBitrate720p;

    @Override
    public IMkBuiltInPreset build() {
        return this;
    }
}
