package uk.gov.hmcts.reform.preapi.email;

import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;

import java.sql.Timestamp;

@SuppressWarnings("PMD.AvoidUncheckedExceptionsInSignatures")
public interface IEmailService {
    EmailResponse recordingReady(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse recordingEdited(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse portalInvite(User to) throws EmailFailedToSendException;

    EmailResponse casePendingClosure(User to, Case forCase, Timestamp date) throws EmailFailedToSendException;

    EmailResponse caseClosed(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse caseClosureCancelled(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse emailVerification(String email, String firstName, String lastName, String verificationCode)
        throws EmailFailedToSendException;
}
