package uk.gov.hmcts.reform.preapi.media.dto;

public interface IMkBuiltInPreset {

    IMkBuiltInPreset build();

    enum MkAssetConverterPreset {
        CopyTopBitrateInterleaved,
        CopyAllBitrateNonInterleaved,
        CopyAllBitrateInterleaved,
        H264SingleBitrate720p
    }
}
