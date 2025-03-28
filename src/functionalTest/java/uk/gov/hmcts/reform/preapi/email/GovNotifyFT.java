package uk.gov.hmcts.reform.preapi.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class GovNotifyFT {
    private static final String TO_EMAIL_ADDRESS = "test@test.com";
    private static final String FROM_EMAIL_ADDRESS = "hmcts.prerecorded.evidence@notifications.service.gov.uk";
    private static final String CASE_REFERENCE = "123456";
    private static final String COURT_NAME = "Court Name";
    private static final String USER_FIRST_NAME = "John";
    private static final String USER_LAST_NAME = "Doe";

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
    @SuppressWarnings("LineLength")
    void recordingReady() {
        var user = createUser();
        var forCase = createCase();

        var response = client.recordingReady(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – New Video", response.getSubject());
        compareBody(
            """
            Hello John Doe,

            A new Pre-recorded Evidence video has been captured for case 123456 at court Court Name.

            Please [login](http://localhost:8080) to the HMCTS Pre-recorded Evidence Portal to review the recording.

            If the link does not work in your email client, copy and paste the following link into your browser:

            http://localhost:8080

            If you have any issues with accessing or playing the recording and require technical support, please phone 0300 323 0194 between the hours of 08:00 and 18:00 weekdays, or 08:30 and 14:00 Saturday.

            Thank you.""", response);
    }

    @DisplayName("Should send recording edited email")
    @Test
    @SuppressWarnings("LineLength")
    void recordingEdited() {
        var user = createUser();
        var forCase = createCase();

        var response = client.recordingEdited(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – Edited Video", response.getSubject());
        compareBody(
            """
            Hello John Doe,

            A new Pre-recorded Evidence video has been edited for case 123456 at court Court Name.

            Please [login](http://localhost:8080) to the HMCTS Pre-recorded Evidence Portal to review the recording.

            If the link does not work in your email client, copy and paste the following link into your browser:

            http://localhost:8080

            If you have any issues with accessing or playing the recording and require technical support, please phone 0300 323 0194 between the hours of 08:00 and 18:00 weekdays, or 08:30 and 14:00 Saturday.

            Thank you.""", response);
    }

    @DisplayName("Should send portal invite email")
    @Test
    @SuppressWarnings("LineLength")
    void portalInvite() {
        var user = createUser();

        var response = client.portalInvite(user);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal Invitation", response.getSubject());
        compareBody(
            """
            Hello John Doe,

            This is an invitation to the HMCTS Pre-recorded Evidence Portal where you can view recorded evidence. \s

            Please use the following [link](http://localhost:8080) to complete your registration. \s

            If the link does not work in your email client, copy and paste the following link into your browser: http://localhost:8080

            ## Registration Instructions

            *   Click 'Sign up now'
            *   Enter your email address and click 'Send verification code'
            *   Check your incoming emails for a verification code, input the verification code and click 'verify code'
            *   Enter your password
            *   Passwords must be at least 8 characters, and must contain characters from at least three of the following four classes: uppercase, lowercase, digit, and non-alphanumeric (special)
            *   Read and agree to the Terms and Conditions to continue.

            Subsequent logins will require Two Factor Authentication (2FA) where you will receive a code via your email address to use as part of your login process. \s
            If you have any issues with accessing or playing the recording and require technical support, please phone 0300 323 0194 between the hours of 08:00 and 18:00 weekdays, or 08:30 and 14:00 Saturday. \s

            Thank you.

            ---

            [Counsel and Judiciary User Guide.pdf](http://localhost:8080/assets/files/user-guide.pdf)

            [PRE Editing Recording Process Quick Guide.pdf](http://localhost:8080/assets/files/process-guide.pdf)

            [PRE FAQs - External.pdf](http://localhost:8080/assets/files/faqs.pdf)

            [PRE Editing Request Form.xlsx](http://localhost:8080/assets/files/pre-editing-request-form.xlsx)""", response);
    }

    @DisplayName("Should send case pending closure email")
    @Test
    @SuppressWarnings("LineLength")
    void casePendingClosure() {
        var user = createUser();
        var forCase = createCase();
        var date = Timestamp.valueOf("2021-01-01 00:00:00.0");

        var response = client.casePendingClosure(user, forCase, date);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody(
            """
            Dear John Doe,

            Case 123456 has been set to close on 1 January 2021. Once the case has been closed, access to recordings will be removed.

            Kind regards,
            Pre-Recorded Evidence Team""", response);
    }

    @DisplayName("Should send case closed email")
    @Test
    @SuppressWarnings("LineLength")
    void caseClosed() {
        var user = createUser();
        var forCase = createCase();

        var response = client.caseClosed(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody(
            """
            Dear John Doe,

            Case 123456 has now been closed and access to recordings is no longer available.

            Kind regards,
            Pre-Recorded Evidence Team""", response);
    }

    @DisplayName("Should send case closure cancelled email")
    @Test
    @SuppressWarnings("LineLength")
    void caseClosureCancelled() {
        var user = createUser();
        var forCase = createCase();

        var response = client.caseClosureCancelled(user, forCase);
        assertEquals(FROM_EMAIL_ADDRESS, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + CASE_REFERENCE + " access update",
            response.getSubject()
        );
        compareBody(
            """
            Dear John Doe,

            Case 123456 will no longer be closed and access to recordings will remain available.

            Kind regards,
            Pre-Recorded Evidence Team""", response);
    }
}
