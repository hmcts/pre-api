package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditControllerFT extends FunctionalTestBase {

    @DisplayName("Should fail to update an audit record as they are immutable")
    @Test
    void updateAuditFailure() throws JsonProcessingException {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var success = putAudit(audit);
        assertResponseCode(success, 201);

        var error = putAudit(audit);
        assertResponseCode(error, 400);
        assertThat(error.body().jsonPath().getString("message"))
            .isEqualTo("Data is immutable and cannot be changed. Id: " + audit.getId());
    }

    private Response putAudit(CreateAuditDTO dto) throws JsonProcessingException {
        return doPutRequest(
            AUDIT_ENDPOINT + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }
}
