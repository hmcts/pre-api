package uk.gov.hmcts.reform.preapi.email;

import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;

public interface IEmailService {
    EmailResponse recordingReady(User to, Case forCase);

    EmailResponse recordingEdited(User to, Case forCase);

    EmailResponse portalInvite(User to);

    EmailResponse casePendingClosure(User to, Case forCase, Timestamp date);

    EmailResponse caseClosed(User to, Case forCase);

    EmailResponse caseClosureCancelled(User to, Case forCase);

    EmailResponse emailVerification(String email, String firstName, String lastName, String verificationCode);
}
