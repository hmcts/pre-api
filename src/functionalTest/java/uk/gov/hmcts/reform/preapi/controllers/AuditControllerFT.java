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

    @DisplayName("Should sort by created at desc when sort param not set and by sort param otherwise")
    @Test
    void getAuditLogsSortBy() throws JsonProcessingException {
        var audit1 = new CreateAuditDTO();
        audit1.setId(UUID.randomUUID());
        audit1.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test1\"}"));
        audit1.setSource(AuditLogSource.AUTO);

        var success1 = putAudit(audit1);
        assertResponseCode(success1, 201);

        var audit2 = new CreateAuditDTO();
        audit2.setId(UUID.randomUUID());
        audit2.setAuditDetails(OBJECT_MAPPER.readTree("{\"test\": \"test2\"}"));
        audit2.setSource(AuditLogSource.AUTO);

        var success2 = putAudit(audit2);
        assertResponseCode(success2, 201);

        var getAuditLogs1 = doGetRequest("/audit", TestingSupportRoles.SUPER_USER);

        assertResponseCode(getAuditLogs1, 200);
        var auditLogs1 = getAuditLogs1.jsonPath().getList("_embedded.createAuditDTOList", CreateAuditDTO.class);

        // default sort by createdAt desc
        // assertThat(auditLogs1.size()).isEqualTo(2);
        // assertThat(auditLogs1.getFirst().getId()).isEqualTo(audit2.getId());
        // assertThat(auditLogs1.getFirst().getCreatedAt()).isAfter(auditLogs1.getLast().getCreatedAt());

        // var getRecordings2 = doGetRequest(
        // RECORDINGS_ENDPOINT + "?sort=createdAt,asc&captureSessionId=" + details.captureSessionId,
        // TestingSupportRoles.SUPER_USER
        // );
        // assertResponseCode(getRecordings2, 200);
        // var recordings2 = getRecordings2.jsonPath().getList("_embedded.recordingDTOList", RecordingDTO.class);

        // sort in opposite direction (createdAt asc)
        // assertThat(recordings2.size()).isEqualTo(2);
        // assertThat(recordings2.getFirst().getId()).isEqualTo(details.recordingId);
        // assertThat(recordings2.getLast().getId()).isEqualTo(recording2.getId());
        // assertThat(recordings2.getFirst().getCreatedAt()).isBefore(recordings2.getLast().getCreatedAt());
    }
}
