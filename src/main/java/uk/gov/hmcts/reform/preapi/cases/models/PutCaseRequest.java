package uk.gov.hmcts.reform.preapi.cases.models;

import lombok.Data;

import java.util.UUID;

@Data
public class PutCaseRequest {
    UUID courtId;
    String reference;
    Boolean test;
}
