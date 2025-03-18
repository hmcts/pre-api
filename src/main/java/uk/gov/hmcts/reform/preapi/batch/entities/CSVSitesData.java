package uk.gov.hmcts.reform.preapi.batch.entities;

import java.util.List;

public class CSVSitesData {

    private String siteReference;
    private String siteName;
    private String location;
    private String courtName;

    public CSVSitesData() {
    }

    public CSVSitesData(String siteReference, String siteName, String location, String courtName) {
        this.siteReference = siteReference;
        this.siteName = siteName;
        this.location = location;
        this.courtName = courtName;
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

    public String getCourtName() {
        return courtName;
    }

    public void setCourtName(String courtName) {
        this.courtName = courtName;
    }

    public static String extractFullCourtName(String courtReference, List<CSVSitesData> sitesDataList) {
        for (CSVSitesData site : sitesDataList) {
            if (site.getSiteReference().equalsIgnoreCase(courtReference)) {
                return site.getCourtName();  
            }
        }
        return null;  
    }

}
