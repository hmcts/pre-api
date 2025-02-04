package uk.gov.hmcts.reform.preapi.entities.batch;

public class TransformationResult {
    private CleansedData cleansedData;
    private String errorMessage;

    public TransformationResult(CleansedData cleansedData, String errorMessage){
        this.cleansedData = cleansedData;
        this.errorMessage = errorMessage;
    }

    public CleansedData getCleansedData(){
        return cleansedData;
    }

    public String getErrorMessage(){
        return this.errorMessage;
    }

    public void setCleansedData(CleansedData cleansedItem){
        this.cleansedData = cleansedItem;
    }

    public void setErrorMessage(String errorMessage){
        this.errorMessage = errorMessage;
    }
}
