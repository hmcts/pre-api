package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class CaseControllerFT extends FunctionalTestBase {

    private static final String CASES_ENDPOINT = "/cases/";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldCreateACaseWithParticipants() throws JsonProcessingException {

        var testIds = doPostRequest("/testing-support/create-court").body().jsonPath();

        var courtId = java.util.UUID.fromString(testIds.get("courtId"));

        var createCase = new CreateCaseDTO();
        createCase.setId(java.util.UUID.randomUUID());
        createCase.setCourtId(courtId);
        createCase.setReference("FT1234");
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
                                       OBJECT_MAPPER.writeValueAsString(createCase));

        assertThat(putResponse.statusCode()).isEqualTo(201);

        var getResponse = doGetRequest(CASES_ENDPOINT + createCase.getId());
        assertThat(getResponse.statusCode()).isEqualTo(200);
        var caseResponse = OBJECT_MAPPER.readValue(getResponse.body().asString(), CaseDTO.class);
        assertThat(caseResponse.getParticipants().size()).isEqualTo(2);
    }
}
