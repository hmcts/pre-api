package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.io.Serializable;
import java.util.UUID;

/**
 * DTO for {@link Case}.
 */
@Value
public class CaseDto implements Serializable {
    UUID id;
    CourtDto court;
    String reference;
    boolean test;

    public CaseDto(Case aCase) {
        id = aCase.getId();
        court = new CourtDto(aCase.getCourt());
        reference = aCase.getReference();
        test = aCase.isTest();
    }
}
