package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.entities.PostMigratedItemGroup;

@Component
public class PostMigrationItemProcessor implements ItemProcessor<PostMigratedItemGroup, PostMigratedItemGroup> {

    @Override
    public PostMigratedItemGroup process(PostMigratedItemGroup item) throws Exception {
        return item;
    }
}
