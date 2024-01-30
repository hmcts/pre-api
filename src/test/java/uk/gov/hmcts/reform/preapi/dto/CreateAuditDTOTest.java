package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("PMD.LawOfDemeter")
class CreateAuditDTOTest {

    private static Audit auditEntity;

    @BeforeAll
    static void setUp() throws JsonProcessingException {
        var om = new ObjectMapper();
        auditEntity = new Audit();
        auditEntity.setId(UUID.randomUUID());
        auditEntity.setAuditDetails(om.readTree("{\"foo\": \"bar\"}"));
        auditEntity.setActivity("CREATE");
        auditEntity.setCategory("User");
        auditEntity.setFunctionalArea("User Admin");
        auditEntity.setTableName("users");
        auditEntity.setTableRecordId(UUID.randomUUID());
        auditEntity.setSource(AuditLogSource.AUTO);
        auditEntity.setCreatedBy(UUID.randomUUID());
        auditEntity.setCreatedAt(new Timestamp(System.currentTimeMillis()));
    }

    @DisplayName("Should create an Audit model from an Audit entity")
    @Test
    void createCaseFromEntity() {

        var model = new CreateAuditDTO(auditEntity);
        assertThat(model.getId()).isEqualTo(auditEntity.getId());
        assertThat(model.getAuditDetails().get("foo").asText()).isEqualTo("bar");
    }
}
