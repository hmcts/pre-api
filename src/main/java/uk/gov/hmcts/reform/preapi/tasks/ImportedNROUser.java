package uk.gov.hmcts.reform.preapi.tasks;

import java.util.ArrayList;
import java.util.UUID;

public class ImportedNROUser {
    private String firstName;
    private String lastName;
    private String email;
    private String court;
    private UUID courtID;
    private Boolean isDefault;
    private UUID roleID;
    private String userAccess;

    public ImportedNROUser(String firstName, String lastName, String email, String court, UUID courtID,
                           Boolean isDefault, UUID roleID, String userAccess) {
        super();
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.court = court;
        this.courtID = courtID;
        this.isDefault = isDefault;
        this.roleID = roleID;
        this.userAccess = userAccess;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ImportedNROUser{");
        sb.append("firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", email='").append(email).append('\'');
        sb.append(", court='").append(court).append('\'');
        sb.append(", courtId='").append(courtID).append('\'');
        sb.append(", isDefault=").append(isDefault);
        sb.append(", roleId='").append(roleID).append('\'');
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

    public UUID getCourtID() {
        return courtID;
    }

    public void setCourtID(UUID courtID) {
        this.courtID = courtID;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
    }

    public UUID getRoleID() {
        return roleID;
    }

    public void setRoleID(UUID roleID) {
        this.roleID = roleID;
    }

    public String getUserAccess() {
        return userAccess;
    }

    public void setUserAccess(String userAccess) {
        this.userAccess = userAccess;
    }

    public static String[] parseCsvLine(String line) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        for (char ch : line.toCharArray()) {
            if (ch == ',') {
                result.add(currentValue.toString().trim());
                currentValue.setLength(0); // Reset the StringBuilder
            } else {
                currentValue.append(ch);
            }
        }
        // Add the last value
        result.add(currentValue.toString().trim());
        return result.toArray(new String[0]);
    }
}
