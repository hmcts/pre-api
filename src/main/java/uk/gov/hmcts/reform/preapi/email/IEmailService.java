package uk.gov.hmcts.reform.preapi.email;

import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.User;

public interface IEmailService {
    void recordingReady(User to, Case forCase);
    void recordingEdited(User to, Case forCase);
    void portalInvite(User to);
    void casePendingClosure(User to, Case forCase, String date);
    void caseClosed(User to, Case forCase);
    void caseClosureCancelled(User to, Case forCase);
}
