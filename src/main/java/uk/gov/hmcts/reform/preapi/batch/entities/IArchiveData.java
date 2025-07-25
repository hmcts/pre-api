package uk.gov.hmcts.reform.preapi.batch.entities;

import java.time.LocalDateTime;

public interface IArchiveData {

    String getFileName();

    LocalDateTime getCreateTimeAsLocalDateTime();
    
    String getArchiveId();

    String getArchiveName();
}
