package uk.gov.hmcts.reform.preapi.models;


import lombok.Value;

import java.sql.Timestamp;
import java.util.UUID;

@Value
public class Case { //NOPMD - suppressed ShortClassName
    UUID id;
    Court court;
    String reference;
    boolean test;
    Timestamp createdAt;
    Timestamp modifiedAt;
    Timestamp deletedAt;

    public Case(uk.gov.hmcts.reform.preapi.entities.Case caseEntity) {
        id = caseEntity.getId();
        court = new Court(caseEntity.getCourt());
        reference = caseEntity.getReference();
        test = caseEntity.isTest();
        createdAt = caseEntity.getCreatedAt();
        modifiedAt = caseEntity.getModifiedAt();
        deletedAt = caseEntity.getDeletedAt();
    }
}
