package uk.gov.hmcts.reform.preapi.batch.application.writer;

import org.springframework.batch.item.ItemWriter;

public interface MigrationItemWriter<T> extends ItemWriter<T> {

}
