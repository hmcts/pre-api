package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class SearchAudits {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime after;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime before;

    private String functionalArea;

    private AuditLogSource source;

    private String userName;

    private UUID courtId;

    private String caseReference;

    public String getFunctionalArea() {
        return functionalArea != null && !functionalArea.isEmpty() ? functionalArea : null;
    }

    public String getUserName() {
        return userName != null && !userName.isEmpty() ? userName : null;
    }

    public String getCaseReference() {
        return caseReference != null && !caseReference.isEmpty() ? caseReference : null;
    }
}
