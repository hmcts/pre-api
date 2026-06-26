package uk.gov.hmcts.reform.preapi.dto.flow;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;

@Data
@Builder
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaseStateChangeNotificationDTO {

    private EmailType emailType;
    private String caseReference;
    private Timestamp closeDate;
    private String firstName;
    private String lastName;
    private String email;

    public enum EmailType {
        CLOSED,
        PENDING_CLOSURE,
        CLOSURE_CANCELLATION
    }

    public CaseStateChangeNotificationDTO(EmailType notificationType, Case aCase, ShareBooking share) {
        emailType = notificationType;
        caseReference = aCase.getReference();
        closeDate = aCase.getClosedAt();
        firstName = share.getSharedWith().getFirstName();
        lastName = share.getSharedWith().getLastName();
        email = share.getSharedWith().getEmail();
    }
}
