package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@Data
public class SearchCourts {
    private CourtType courtType;
    private String name;
    private String locationCode;
    private String regionName;

    public String getName() {
        return name != null && !name.isEmpty() ? name : null;
    }

    public String getLocationCode() {
        return locationCode != null && !locationCode.isEmpty() ? locationCode : null;
    }

    public String getRegionName() {
        return regionName != null && !regionName.isEmpty() ? regionName : null;
    }
}
