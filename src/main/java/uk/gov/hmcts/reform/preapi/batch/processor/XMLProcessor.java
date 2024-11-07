package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.batch.ArchiveFiles;

import java.util.logging.Logger;

@Component
public class XMLProcessor implements ItemProcessor<ArchiveFiles, ArchiveFiles> {

    @Override
    public ArchiveFiles process(ArchiveFiles archiveFiles) {
        String displayName = archiveFiles.getDisplayName();
        int duration = archiveFiles.getMp4FileGrp().getMp4File().getDuration();
        long creatTime = archiveFiles.getMp4FileGrp().getMp4File().getCreatTime();
        Logger.getAnonymousLogger().info("Display Name: " + displayName);
        Logger.getAnonymousLogger().info("Duration: " + duration);
        Logger.getAnonymousLogger().info("Creation Time: " + creatTime);

        return archiveFiles;
    }
}
