package uk.gov.hmcts.reform.preapi.entities.batch;

import java.util.List;

public class CSVSitesData {

    private String siteReference;
    private String siteName;
    private String location;

    public CSVSitesData() {
    }

    public CSVSitesData(String siteReference, String siteName, String location) {
        this.siteReference = siteReference;
        this.siteName = siteName;
        this.location = location;
    }

    public String getSiteReference() {
        return siteReference;
    }

    public void setSiteReference(String siteReference) {
        this.siteReference = siteReference;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public static String extractFullCourtName(String courtReference, List<CSVSitesData> sitesDataList) {
        for (CSVSitesData site : sitesDataList) {
            if (site.getSiteReference().equalsIgnoreCase(courtReference)) {
                return site.getSiteName();  
            }
        }
        return null;  
    }

}
