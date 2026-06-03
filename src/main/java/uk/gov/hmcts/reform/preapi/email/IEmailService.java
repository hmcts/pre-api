package uk.gov.hmcts.reform.preapi.email;

import uk.gov.hmcts.reform.preapi.email.govnotify.templates.EditEmailParameters;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;
import java.util.Optional;

public interface IEmailService {
    EmailResponse recordingReady(User to, Case forCase);

    EmailResponse recordingEdited(User to, Case forCase);

    EmailResponse portalInvite(User to);

    EmailResponse casePendingClosure(User to, Case forCase, Timestamp date);

    EmailResponse caseClosed(User to, Case forCase);

    EmailResponse caseClosureCancelled(User to, Case forCase);

    EmailResponse emailVerification(String email, String firstName, String lastName, String verificationCode);

    Optional<EmailResponse> sendEmailAboutEditingRequest(EditEmailParameters editEmailParameters);
}
