package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditDTOTest {

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

    @Test
    @DisplayName("Should create an Audit model from an Audit entity")
    void createCaseFromEntity() {
        assertAuditDtoValid(new AuditDTO(auditEntity));
    }

    @Test
    @DisplayName("Should create an Audit model from an Audit and User entity")
    public void createCaseFromAuditAndUser() {
        var user = new User();
        user.setId(auditEntity.getCreatedBy());
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setPhone("1234567890");
        user.setOrganisation("Example Organisation");

        var model = new AuditDTO(auditEntity, user);
        assertAuditDtoValid(model);

        var userDto = model.getCreatedBy();
        assertThat(userDto.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(userDto.getLastName()).isEqualTo(user.getLastName());
        assertThat(userDto.getEmail()).isEqualTo(user.getEmail());
        assertThat(userDto.getPhoneNumber()).isEqualTo(user.getPhone());
        assertThat(userDto.getOrganisation()).isEqualTo(user.getOrganisation());
    }

    private void assertAuditDtoValid(AuditDTO model) {
        assertThat(model.getId()).isEqualTo(auditEntity.getId());
        assertThat(model.getCreatedAt()).isEqualTo(auditEntity.getCreatedAt());
        assertThat(model.getCreatedBy().getId()).isEqualTo(auditEntity.getCreatedBy());
        assertThat(model.getAuditDetails().get("foo").asText()).isEqualTo("bar");
        assertThat(model.getActivity()).isEqualTo(auditEntity.getActivity());
        assertThat(model.getCategory()).isEqualTo(auditEntity.getCategory());
        assertThat(model.getFunctionalArea()).isEqualTo(auditEntity.getFunctionalArea());
        assertThat(model.getTableName()).isEqualTo(auditEntity.getTableName());
        assertThat(model.getTableRecordId()).isEqualTo(auditEntity.getTableRecordId());
        assertThat(model.getSource()).isEqualTo(auditEntity.getSource());
    }
}
