package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationRecordingVersion;
import uk.gov.hmcts.reform.preapi.batch.application.enums.VfMigrationStatus;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.migration.CreateVfMigrationRecordDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

public class VfMigrationControllerFT extends FunctionalTestBase {
    private static final String MIGRATION_RECORD_ENDPOINT = "/vf-migration-records";

    @NullSource
    @ParameterizedTest
    @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should return forbidden for users attempting to get migration records")
    void getMigrationRecordsAuth(TestingSupportRoles role) {
        Response response = doGetRequest(MIGRATION_RECORD_ENDPOINT, role);
        assertResponseCode(response, role == null ? 401 : 403);
    }

    @NullSource
    @ParameterizedTest
    @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should return forbidden for users attempting to update migration records")
    void putMigrationRecordsAuth(TestingSupportRoles role) throws JsonProcessingException {
        CreateVfMigrationRecordDTO dto = new CreateVfMigrationRecordDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(VfMigrationStatus.PENDING);
        dto.setRecordingVersion(VfMigrationRecordingVersion.ORIG);
        dto.setUrn("1234567890");
        dto.setExhibitReference("1234567890");
        dto.setCourtId(UUID.randomUUID());
        dto.setWitnessName("Witness Name");
        dto.setDefendantName("Defendant Name");
        Response response = doPutRequest(
            MIGRATION_RECORD_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            role
        );
        assertResponseCode(response, role == null ? 401 : 403);
    }

    @NullSource
    @ParameterizedTest
    @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should return forbidden for users attempting call submit endpoint")
    void postSubmitAuth(TestingSupportRoles role) {
        Response response = doPostRequest(
            MIGRATION_RECORD_ENDPOINT + "/submit",
            role
        );
        assertResponseCode(response, role == null ? 401 : 403);
    }
}
