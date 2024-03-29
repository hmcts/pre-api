package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    @DisplayName("Should create a case with participants")
    @Test
    void shouldCreateACaseWithParticipants() throws JsonProcessingException {

        var testIds = doPostRequest("/testing-support/create-court", false).body().jsonPath();

        var courtId = UUID.fromString(testIds.get("courtId"));

        var createCase = new CreateCaseDTO();
        createCase.setId(UUID.randomUUID());
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

        var putResponse = doPutRequest(CASES_ENDPOINT + "/" + createCase.getId(),
                                       OBJECT_MAPPER.writeValueAsString(createCase), true);

        assertResponseCode(putResponse, 201);

        var getResponse = doGetRequest(CASES_ENDPOINT + "/" + createCase.getId(), true);
        assertResponseCode(getResponse, 200);
        var caseResponse = OBJECT_MAPPER.readValue(getResponse.body().asString(), CaseDTO.class);
        assertThat(caseResponse.getParticipants().size()).isEqualTo(2);
    }

    @DisplayName("Unauthorised use of endpoints should return 401")
    @Test
    void unauthorisedRequestsReturn401() throws JsonProcessingException {
        var getCaseByIdResponse = doGetRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), false);
        assertResponseCode(getCaseByIdResponse, 401);

        var getCasesResponse = doGetRequest(CASES_ENDPOINT, false);
        assertResponseCode(getCasesResponse, 401);

        var putCaseResponse = doPutRequest(
            CASES_ENDPOINT + "/" + UUID.randomUUID(),
            OBJECT_MAPPER.writeValueAsString(new CreateBookingDTO()),
            false
        );
        assertResponseCode(putCaseResponse, 401);

        var deleteCaseResponse = doDeleteRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), false);
        assertResponseCode(deleteCaseResponse, 401);
    }

    @DisplayName("Scenario: Delete case")
    @Test
    void shouldDeleteCaseWithExistingId() throws JsonProcessingException {
        var caseDTO = createCase();

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseDTO.getId(),
            OBJECT_MAPPER.writeValueAsString(caseDTO),
            true
        );
        assertResponseCode(putCase, 201);

        var getCasesResponse = doGetRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(getCasesResponse, 200);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(deleteResponse, 200);

        var getCasesResponse2 = doGetRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(getCasesResponse2, 404);
    }

    @DisplayName("Should fail to delete a case when it is already deleted")
    @Test
    void shouldDeleteCaseWithExistingIdFail() throws JsonProcessingException {
        var caseDTO = createCase();

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseDTO.getId(),
            OBJECT_MAPPER.writeValueAsString(caseDTO),
            true
        );
        assertResponseCode(putCase, 201);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(deleteResponse, 200);

        var deleteResponse2 = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(deleteResponse2, 404);
    }

    @DisplayName("Should fail to delete a case that doesn't exist")
    @Test
    void shouldDeleteCaseWithNonExistingIdFail() {
        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), true);
        assertResponseCode(deleteResponse, 404);
    }
}
