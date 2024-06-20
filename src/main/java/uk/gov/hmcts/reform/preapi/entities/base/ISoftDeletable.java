package uk.gov.hmcts.reform.preapi.entities.base;

public interface ISoftDeletable {

    public void setDeleteOperation(boolean deleteOperation);

    public boolean isDeleteOperation();
}
