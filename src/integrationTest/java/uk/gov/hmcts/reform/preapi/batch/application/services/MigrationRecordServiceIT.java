package uk.gov.hmcts.reform.preapi.batch.application.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.batch.entities.MigrationRecord;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class MigrationRecordServiceIT extends IntegrationTestBase {

    @Autowired
    private MigrationRecordService migrationRecordService;

    @Test
    @Transactional
    public void testFindByArchiveId() {
        MigrationRecord migrationRecord = new MigrationRecord();
        migrationRecord.setId(UUID.randomUUID());
        migrationRecord.setArchiveId(UUID.randomUUID().toString());
        migrationRecord.setArchiveName("archive_name");
        migrationRecord.setStatus(VfMigrationStatus.PENDING);

        entityManager.persist(migrationRecord);
        entityManager.flush();

        Optional<MigrationRecord> result = migrationRecordService.findByArchiveId(migrationRecord.getArchiveId());

        assertThat(result.isPresent()).isTrue();
        assertThat(result.get().getId()).isEqualTo(migrationRecord.getId());
    }
}
