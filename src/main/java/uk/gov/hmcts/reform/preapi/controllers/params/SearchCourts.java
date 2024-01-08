package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import uk.gov.hmcts.reform.preapi.enums.CourtType;

@Data
public class SearchCourts {
    private CourtType courtType;
    private String name;
    private String locationCode;
    private String regionName;
}
