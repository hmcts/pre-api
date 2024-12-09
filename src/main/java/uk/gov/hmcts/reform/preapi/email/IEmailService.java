package uk.gov.hmcts.reform.preapi.email;

import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.exception.EmailFailedToSendException;

public interface IEmailService {
    EmailResponse recordingReady(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse recordingEdited(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse portalInvite(User to) throws EmailFailedToSendException;

    EmailResponse casePendingClosure(User to, Case forCase, String date) throws EmailFailedToSendException;

    EmailResponse caseClosed(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse caseClosureCancelled(User to, Case forCase) throws EmailFailedToSendException;

    EmailResponse editingJointlyAgreed(String to, EditRequest editRequest) throws EmailFailedToSendException;

    EmailResponse editingNotJointlyAgreed(String to, EditRequest editRequest) throws EmailFailedToSendException;

    EmailResponse editingRejected(String to, EditRequest editRequest) throws EmailFailedToSendException;
}
