package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;

import static org.assertj.core.api.Assertions.assertThat;

class PostMigrationItemProcessorTest {

    private PostMigrationItemProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new PostMigrationItemProcessor();
    }

    @Test
    void process_shouldReturnSameItem() throws Exception {
        PostMigratedItemGroup item = new PostMigratedItemGroup();

        PostMigratedItemGroup result = processor.process(item);

        assertThat(result).isSameAs(item);
    }
}
