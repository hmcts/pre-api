package uk.gov.hmcts.reform.preapi.email;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;

import static org.junit.jupiter.api.Assertions.assertEquals;


@SpringBootTest
public class GovNotifyFT {
    private final String TO_EMAIL_ADDRESS = "test@test.com";
    private final String FROM_EMAIL_ADDRESS = "prerecorded.evidence@notifications.service.gov.uk";
    private final String CASE_REFERENCE = "123456";
    private final String COURT_NAME = "Court Name";
    private final String USER_FIRST_NAME = "John";
    private final String USER_LAST_NAME = "Doe";

    @Value("${portal.url")
    private String portalUrl;

    @Autowired
    GovNotify client;

    private User createUser() {
        var user = new User();
        user.setFirstName(USER_FIRST_NAME);
        user.setLastName(USER_LAST_NAME);
        user.setEmail(TO_EMAIL_ADDRESS);
        return user;
    }

    private Case createCase() {
        var court = new Court();
        court.setName(COURT_NAME);
        var forCase = new Case();
        forCase.setCourt(court);
        forCase.setReference(CASE_REFERENCE);
        return forCase;
    }

    private void compareBody(String expected, EmailResponse emailResponse) {
        String actualUnix = emailResponse.getBody().replace("\r\n", "\n");
        assertEquals(expected, actualUnix);
    }

    @DisplayName("Should send recording ready email")
    @Test
    void recordingReady() {
        var user = createUser();
        var forCase = createCase();

        var response = client.recordingReady(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – New Video", response.getSubject());
        compareBody("""
                        """, response);
    }

    @DisplayName("Should send recording edited email")
    @Test
    void recordingEdited() {
        var user = createUser();
        var forCase = createCase();

        var response = client.recordingEdited(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – Edited Video", response.getSubject());
        compareBody("""
                        """, response);
    }

    @DisplayName("Should send portal invite email")
    @Test
    void portalInvite() {
        var user = createUser();
        var userGuideLink = portalUrl + "/user-guide";
        var processGuideLink = portalUrl + "/process-guide";
        var faqsLink = portalUrl + "/faqs";

        var response = client.portalInvite(user);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal Invitation", response.getSubject());
        compareBody("""
                        """, response);
    }

    @DisplayName("Should send case pending closure email")
    @Test
    void casePendingClosure() {
        var user = createUser();
        var forCase = createCase();
        var date = "2021-01-01";

        var response = client.casePendingClosure(user, forCase, date);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody("""
                        """, response);
    }

    @DisplayName("Should send case closed email")
    @Test
    void caseClosed() {
        var user = createUser();
        var forCase = createCase();

        var response = client.caseClosed(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody("""
                        """, response);
    }

    @DisplayName("Should send case closure cancelled email")
    @Test
    void caseClosureCancelled() {
        var user = createUser();
        var forCase = createCase();

        var response = client.caseClosureCancelled(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody("""
                        """, response);
    }
}
