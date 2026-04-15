package uk.gov.hmcts.reform.preapi.batch.application.processor;

public record VodafonePreferredMp4Metadata(String xmlBlobName,
                                           String archiveId,
                                           String blobPath,
                                           String fileName) {
}
