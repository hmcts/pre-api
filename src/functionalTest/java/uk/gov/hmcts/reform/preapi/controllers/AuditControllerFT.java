package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AuditControllerFT  extends FunctionalTestBase {

    @DisplayName("Should fail to update an audit record as they are immutable")
    @Test
    void updateAuditFailure() throws JsonProcessingException {
        var audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);

        var success = doPutRequest(AUDIT_ENDPOINT + audit.getId(), OBJECT_MAPPER.writeValueAsString(audit), true);
        assertResponseCode(success, 201);

        var error = doPutRequest(AUDIT_ENDPOINT + audit.getId(), OBJECT_MAPPER.writeValueAsString(audit), true);
        assertResponseCode(error, 400);
        assertThat(error.body().jsonPath().getString("message"))
            .isEqualTo("Data is immutable and cannot be changed. Id: " + audit.getId());
    }
}
