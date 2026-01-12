package uk.gov.hmcts.reform.preapi.entities.base;

public interface ISoftDeletable {
    void setDeleteOperation(boolean deleteOperation);

    boolean isDeleteOperation();
}
