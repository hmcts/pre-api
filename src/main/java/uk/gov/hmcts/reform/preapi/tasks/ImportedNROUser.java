package uk.gov.hmcts.reform.preapi.tasks;

public class ImportedNROUser {
    private String firstName;
    private String lastName;
    private String email;
    private String court;
    private Boolean isDefault;
    private String userAccess;

    public ImportedNROUser(String firstName, String lastName, String email, String court, Boolean isDefault,
                    String userAccess) {
        super();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.court = court;
        this.isDefault = isDefault;
        this.userAccess = userAccess;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("ImportedNROUser{");
        sb.append("firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", email='").append(email).append('\'');
        sb.append(", court='").append(court).append('\'');
        sb.append(", isDefault=").append(isDefault);
        sb.append(", userAccess='").append(userAccess).append('\'');
        sb.append('}');
        return sb.toString();
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCourt() {
        return court;
    }

    public void setCourt(String court) {
        this.court = court;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        isDefault = isDefault;
    }

    public String getUserAccess() {
        return userAccess;
    }

    public void setUserAccess(String userAccess) {
        this.userAccess = userAccess;
    }
}
