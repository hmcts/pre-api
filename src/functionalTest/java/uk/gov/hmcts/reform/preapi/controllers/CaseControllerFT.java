package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseControllerFT extends FunctionalTestBase {

    private static final String CASES_ENDPOINT = "/cases/";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCreateACaseWithParticipants() throws JsonProcessingException {

        var testIds = doPostRequest("/testing-support/create-court", false).body().jsonPath();

        var courtId = java.util.UUID.fromString(testIds.get("courtId"));

        var createCase = new CreateCaseDTO();
        createCase.setId(java.util.UUID.randomUUID());
        createCase.setCourtId(courtId);
        createCase.setReference(generateRandomCaseReference());
        var participant1 = new CreateParticipantDTO();
        participant1.setId(java.util.UUID.randomUUID());
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        participant1.setParticipantType(ParticipantType.DEFENDANT);
        var participant2 = new CreateParticipantDTO();
        participant2.setId(java.util.UUID.randomUUID());
        participant2.setFirstName("John");
        participant2.setLastName("Smith");
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        createCase.setParticipants(java.util.Set.of(
            participant1,
            participant2
        ));

        var putResponse = doPutRequest(CASES_ENDPOINT + createCase.getId(),
                                       OBJECT_MAPPER.writeValueAsString(createCase), true);

        assertThat(putResponse.statusCode()).isEqualTo(201);

        var getResponse = doGetRequest(CASES_ENDPOINT + createCase.getId(), true);
        assertThat(getResponse.statusCode()).isEqualTo(200);
        var caseResponse = OBJECT_MAPPER.readValue(getResponse.body().asString(), CaseDTO.class);
        assertThat(caseResponse.getParticipants().size()).isEqualTo(2);
    }

    @DisplayName("Unauthorised use of endpoints should return 401")
    @Test
    void unauthorisedRequestsReturn401() throws JsonProcessingException {
        var getCaseByIdResponse = doGetRequest(CASES_ENDPOINT + UUID.randomUUID(), false);
        assertResponse401(getCaseByIdResponse);

        var getCasesResponse = doGetRequest("/cases", false);
        assertResponse401(getCasesResponse);

        var putCaseResponse = doPutRequest(
            CASES_ENDPOINT + UUID.randomUUID(),
            OBJECT_MAPPER.writeValueAsString(new CreateBookingDTO()),
            false
        );
        assertResponse401(putCaseResponse);

        var deleteCaseResponse = doDeleteRequest(CASES_ENDPOINT + UUID.randomUUID(), false);
        assertResponse401(deleteCaseResponse);
    }

    private void assertResponse401(Response response) {
        assertThat(response.statusCode()).isEqualTo(401);
    }

    private String generateRandomCaseReference() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 13);
    }
}
