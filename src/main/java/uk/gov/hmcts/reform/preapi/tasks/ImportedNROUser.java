package uk.gov.hmcts.reform.preapi.tasks;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ImportedNROUser {
    private String firstName;
    private String lastName;
    private String email;
    private String court;
    private UUID courtID;
    private Boolean isDefault;
    private UUID roleID;
    private String userAccess;

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
