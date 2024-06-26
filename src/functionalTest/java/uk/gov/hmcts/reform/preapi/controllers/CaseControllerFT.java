package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Create/update a case")
    @Test
    void shouldCreateAndUpdateCase() throws JsonProcessingException {
        var dto = createCase();

        // create a case
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update a case
        dto.setTest(true);
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 204);
        assertMatchesDto(dto);
    }

    @DisplayName("Scenario: Create a case with a non-existing court")
    @Test
    void shouldNotCreateAndUpdateCaseWithNonExistingCourt() throws JsonProcessingException {
        var dto = createCase();
        dto.setCourtId(UUID.randomUUID());
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 404);
        assertThat(putCase1.body().jsonPath().getString("message")).isEqualTo("Not found: Court: " + dto.getCourtId());
    }

    @DisplayName("Should create a case with participants")
    @Test
    void shouldCreateACaseWithParticipants() throws JsonProcessingException {
        var participant1 = new CreateParticipantDTO();
        participant1.setId(UUID.randomUUID());
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        participant1.setParticipantType(ParticipantType.DEFENDANT);
        var participant2 = new CreateParticipantDTO();
        participant2.setId(UUID.randomUUID());
        participant2.setFirstName("John");
        participant2.setLastName("Smith");
        participant2.setParticipantType(ParticipantType.WITNESS);

        var createCase = createCase();
        createCase.setParticipants(Set.of(
            participant1,
            participant2
        ));

        var putResponse = putCase(createCase);
        assertResponseCode(putResponse, 201);

        var getResponse = assertCaseExists(createCase.getId(), true);
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

        var putCase = putCase(caseDTO);

        assertResponseCode(putCase, 201);
        assertCaseExists(caseDTO.getId(), true);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), true);
        assertResponseCode(deleteResponse, 200);
        assertCaseExists(caseDTO.getId(), false);
    }

    @DisplayName("Should fail to delete a case when it is already deleted")
    @Test
    void shouldDeleteCaseWithExistingIdFail() throws JsonProcessingException {
        var caseDTO = createCase();
        var putCase = putCase(caseDTO);
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

    @DisplayName("Scenario: Remove Case Reference")
    @Test
    void shouldFailToRemoveCaseReference() throws JsonProcessingException {
        var caseDTO = createCase();

        caseDTO.setReference(null);
        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("must not be null");

        caseDTO.setReference("");
        var putResponse2 = putCase(caseDTO);
        assertResponseCode(putResponse2, 400);
        assertThat(putResponse2.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Cannot create a case with reference of more than 13 characters")
    @Test
    void shouldFailTopUpdateCaseWithLongReference() throws JsonProcessingException {
        var caseDTO = createCase();

        caseDTO.setReference("FOURTEEN_CHARS");
        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Cannot create a case with reference of less that 9 characters")
    @Test
    void shouldFailTopUpdateCaseWithShortReference() throws JsonProcessingException {
        var caseDTO = createCase();
        caseDTO.setReference("12345678");

        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Create a case with a duplicate case reference in the same court")
    @Test
    void shouldFailCreateCaseWithDuplicateReferenceInSameCourt() throws JsonProcessingException {
        var caseDTO1 = createCase();
        var putResponse1 = putCase(caseDTO1);
        assertResponseCode(putResponse1, 201);

        var caseDTO2 = new CreateCaseDTO();
        caseDTO2.setId(UUID.randomUUID());
        caseDTO2.setReference(caseDTO1.getReference());
        caseDTO2.setCourtId(caseDTO1.getCourtId());
        caseDTO2.setParticipants(Set.of());
        caseDTO2.setTest(false);
        caseDTO2.setParticipants(Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        ));

        var putResponse2 = putCase(caseDTO2);
        assertResponseCode(putResponse2, 409);
        assertThat(putResponse2.getBody().jsonPath().getString("message"))
            .isEqualTo("Conflict: Case reference is already in use for this court");
    }

    @DisplayName("Scenario: Create a case with a duplicate case reference in different court")
    @Test
    void shouldCreateCaseWithDuplicateReferenceInDifferentCourt() throws JsonProcessingException {
        var caseDTO1 = createCase();
        var putResponse1 = putCase(caseDTO1);
        assertResponseCode(putResponse1, 201);

        var caseDTO2 = createCase();
        caseDTO2.setReference(caseDTO1.getReference());

        var putResponse2 = putCase(caseDTO2);
        assertResponseCode(putResponse2, 201);
    }

    @DisplayName("Scenario: Restore case")
    @Test
    void shouldUndeleteCase() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(deleteResponse, 200);
        assertCaseExists(dto.getId(), false);

        var undeleteResponse = doPostRequest(CASES_ENDPOINT + "/" + dto.getId() + "/undelete", true);
        assertResponseCode(undeleteResponse, 200);
        assertCaseExists(dto.getId(), true);
    }

    @DisplayName("Scenario: Get Case by case id")
    @Test
    void shouldGetCaseByCaseId() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        var getCase = assertCaseExists(dto.getId(), true);
        assertThat(getCase.body().jsonPath().getUUID("id")).isEqualTo(dto.getId());
    }

    @DisplayName("Scenario: Get non-existing case by case id ")
    @Test
    void shouldGetNonExistingCaseByCaseId() {
        var id = UUID.randomUUID();
        assertCaseExists(id, false);
    }

    @DisplayName("Scenario: Search Cases by case reference")
    @Test
    void shouldSearchCaseByCaseReference() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        // match
        var getCases1 = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), true);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases1.body().jsonPath().getString("_embedded.caseDTOList[0].reference"))
            .isEqualTo(dto.getReference());

        // match lowercase
        var getCases2 = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference().toLowerCase(), true);
        assertResponseCode(getCases2, 200);
        assertThat(getCases2.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases2.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());

        // match uppercase
        var getCases3 = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference().toUpperCase(), true);
        assertResponseCode(getCases3, 200);
        assertThat(getCases3.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases3.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());

        // match partial
        var getCases4 = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference().substring(1, 12), true);
        assertResponseCode(getCases4, 200);
        assertThat(getCases4.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases4.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
    }

    @DisplayName("Scenario: Search Cases by court id")
    @Test
    void shouldSearchCaseByCourtId() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        var getCases1 = doGetRequest(CASES_ENDPOINT + "?courtId=" + dto.getCourtId(), true);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].court.id"))
            .isEqualTo(dto.getCourtId());
    }

    @DisplayName("Scenario: Search by Cases and include deleted case")
    @Test
    void shouldSearchCaseAndIncludeDeletedCase() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        // delete the case
        var deleteCase = doDeleteRequest(CASES_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(deleteCase, 200);
        assertCaseExists(dto.getId(), false);

        // search without including deleted
        var getCases1 = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), true);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList")).isNullOrEmpty();

        // search including deleted
        var getCases2 = doGetRequest(
            CASES_ENDPOINT + "?reference=" + dto.getReference() + "&includeDeleted=true",
            true
        );
        assertResponseCode(getCases2, 200);
        assertThat(getCases2.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases2.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases2.body().jsonPath().getString("_embedded.caseDTOList[0].reference"))
            .isEqualTo(dto.getReference());
    }

    private Response putCase(CreateCaseDTO dto) throws JsonProcessingException {
        return doPutRequest(CASES_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    private void assertMatchesDto(CreateCaseDTO dto) {
        var getCase = assertCaseExists(dto.getId(), true);
        var res = getCase.body().as(CaseDTO.class);
        assertThat(res).isNotNull();
        assertThat(res.getCourt().getId()).isEqualTo(dto.getCourtId());
        assertThat(res.getReference()).isEqualTo(dto.getReference());
        assertThat(res.getParticipants()).hasSize(dto.getParticipants().size());
        assertThat(res.isTest()).isEqualTo(dto.isTest());
        assertThat(res.getCreatedAt()).isNotNull();
        assertThat(res.getModifiedAt()).isNotNull();
        assertThat(res.getDeletedAt()).isNull();
    }
}
