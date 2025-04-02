package uk.gov.hmcts.reform.preapi.batch.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CSVSitesData {
    private String siteReference;
    private String siteName;
    private String location;
    private String courtName;

    // TODO remove used ?
    public static String extractFullCourtName(String courtReference, List<CSVSitesData> sitesDataList) {
        for (CSVSitesData site : sitesDataList) {
            if (site.getSiteReference().equalsIgnoreCase(courtReference)) {
                return site.getCourtName();
            }
        }
        return null;
    }
}
