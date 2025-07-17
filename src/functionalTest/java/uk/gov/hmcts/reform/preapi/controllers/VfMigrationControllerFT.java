package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

public class VfMigrationControllerFT extends FunctionalTestBase {
    private static final String MIGRATION_RECORD_ENDPOINT = "/vf-migration-records";

    @NullSource
    @ParameterizedTest
    @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should return forbidden for users attempting to get migration records")
    void getMigrationRecordsAuth(TestingSupportRoles role) {
        var response = doGetRequest(MIGRATION_RECORD_ENDPOINT, role);
        assertResponseCode(response, role == null ? 401 : 403);
    }
}
