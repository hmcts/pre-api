package uk.gov.hmcts.reform.preapi.cases.models;

import lombok.Data;

import java.io.Serializable;
import java.util.UUID;

@Data
public class PutCaseRequestModel implements Serializable {
    UUID courtId;
    String reference;
    Boolean test;
}
