package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CourtEmailDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.List;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;

class CourtControllerFT extends FunctionalTestBase {

    private static final String INPUT_CSV_PATH = "src/functionalTest/resources/test/courts/email_addresses.csv";

    @DisplayName("Scenario: Create and update a court")
    @Test
    void createAndUpdateCourt() throws JsonProcessingException {
        var dto = createCourt();

        // create court
        var createResponse = putCourt(dto);
        assertResponseCode(createResponse, 201);
        var courtResponse1 = assertCourtExists(dto.getId(), true);
        assertThat(courtResponse1.body().jsonPath().getString("name")).isEqualTo(dto.getName());

        // update court
        dto.setName("Updated Court");
        var updateResponse = putCourt(dto);
        assertResponseCode(updateResponse, 204);
        var courtResponse2 = assertCourtExists(dto.getId(), true);
        assertThat(courtResponse2.body().jsonPath().getString("name")).isEqualTo(dto.getName());
    }

    @Test
    @DisplayName("Should update court email addresses from a csv file")
    void updateCourtEmailAddressesFromCsv() throws JsonProcessingException {

        // create courts
        var dto = createCourt();
        var createResponse = putCourt(dto);
        assertResponseCode(createResponse, 201);
        var courtResponse1 = assertCourtExists(dto.getId(), true);
        assertThat(courtResponse1.body().jsonPath().getString("name")).isEqualTo(dto.getName());

        // Before update
        assertThat(courtResponse1.body().jsonPath().getString("group_email")).isNull();

        Response postResponse = doPostRequestWithMultipart(
            COURTS_ENDPOINT + "/email",
            MULTIPART_HEADERS,
            INPUT_CSV_PATH,
            TestingSupportRoles.SUPER_USER
        );

        // After update
        List<CourtEmailDTO> returnedCourts = postResponse.getBody().jsonPath().getList("", CourtEmailDTO.class);

        CourtEmailDTO updatedCourt = returnedCourts.stream()
            .filter(courtDTO -> courtDTO.getName().equals(dto.getName()))
            .findFirst()
            .orElseThrow(() ->
                             new NotFoundException(format(
                                 "Did not find expected court %s in returned data",
                                 dto.getName()
                             )));

        assertThat(updatedCourt.getGroupEmail()).isEqualTo("PRE.Edits.Example@justice.gov.uk");
    }

}
