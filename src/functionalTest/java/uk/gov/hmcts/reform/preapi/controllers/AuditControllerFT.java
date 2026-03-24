package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditControllerFT extends FunctionalTestBase {

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

    @DisplayName("Should not put an audit with un-sanitised data")
    @Test
    void updateAuditFailureWithUnsafeData() throws JsonProcessingException {
        CreateAuditDTO audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);
        audit.setActivity("<script>alert(1)</script>");
        audit.setFunctionalArea("<img src='x' onerror='alert(1)'>");

        var error = putAudit(audit);
        assertResponseCode(error, 400);
        assertThat(error.body().jsonPath().getString("activity"))
            .contains("potentially malicious content");
        assertThat(error.body().jsonPath().getString("functionalArea"))
            .contains("potentially malicious content");
    }

    @DisplayName("Should not put an audit with un-sanitised audit details JSON")
    @Test
    void updateAuditFailureWithUnsafeAuditDetailsData() throws JsonProcessingException {
        CreateAuditDTO audit = new CreateAuditDTO();
        audit.setId(UUID.randomUUID());
        audit.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test\"}"));
        audit.setSource(AuditLogSource.AUTO);
        audit.setActivity("Nice Activity");
        audit.setFunctionalArea("Nice Area");
        JsonNode
            unsafeNode = OBJECT_MAPPER.readTree("{\"test\": \"<script>alert(1)</script>\", "
                                                    + "\"test2\": {\"nested\": "
                                                    + "\"<img src='x' onerror='alert(1)'>\"}}}");
        audit.setAuditDetails(unsafeNode);

        var error = putAudit(audit);
        assertResponseCode(error, 400);
        assertThat(error.getBody().asPrettyString())
            .contains("potentially malicious content");
    }

    private Response putAudit(CreateAuditDTO dto) throws JsonProcessingException {
        return doPutRequest(
            AUDIT_ENDPOINT + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }
}
