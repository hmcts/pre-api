package uk.gov.hmcts.reform.preapi.email;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class GovNotifyFT {
    private final String toEmailAddress = "test@test.com";
    private final String fromEmailAddress = "prerecorded.evidence@notifications.service.gov.uk";
    private final String caseReference = "123456";
    private final String courtName = "Court Name";
    private final String userFirstName = "John";
    private final String userLastName = "Doe";

    @Value("${portal.url")
    private String portalUrl;

    @Autowired
    private GovNotify client;

    private User createUser() {
        var user = new User();
        user.setFirstName(userFirstName);
        user.setLastName(userLastName);
        user.setEmail(toEmailAddress);
        return user;
    }

    private Case createCase() {
        var court = new Court();
        court.setName(courtName);
        var forCase = new Case();
        forCase.setCourt(court);
        forCase.setReference(caseReference);
        return forCase;
    }

    private Participant createParticipant(ParticipantType type) {
        var participant = new Participant();
        participant.setFirstName("First");
        participant.setLastName("Last");
        participant.setParticipantType(type);
        return participant;
    }

    private EditRequest createEditRequest() {
        var aCase = createCase();
        var booking = new Booking();
        booking.setCaseId(aCase);
        booking.setParticipants(Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)));
        var captureSession = new CaptureSession();
        captureSession.setBooking(booking);
        var recording = new Recording();
        recording.setCaptureSession(captureSession);
        var request = new EditRequest();
        request.setSourceRecording(recording);
        request.setEditInstruction(
            "{\"requestedInstructions\":"
                + "[{\"start_of_cut\":\"00:00:00\",\"end_of_cut\":\"00:00:30\",\"reason\":\"\",\"start\":0,\"end\":0}],"
                + "\"ffmpegInstructions\":[]}");
        return request;
    }

    private void compareBody(String expected, EmailResponse emailResponse) {
        String actualUnix = emailResponse.getBody().replace("\r\n", "\n").replace("\s\n", "\n");
        assertEquals(expected, actualUnix);
    }

    @DisplayName("Should send recording ready email")
    @Test
    @SuppressWarnings("LineLength")
    void recordingReady() {
        var user = createUser();
        var forCase = createCase();

        var response = client.recordingReady(user, forCase);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – New Video", response.getSubject());
        compareBody(
            """
            Hello John,

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
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal – Edited Video", response.getSubject());
        compareBody(
            """
            Hello John,

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
        var userGuideLink = portalUrl + "/user-guide";
        var processGuideLink = portalUrl + "/process-guide";
        var faqsLink = portalUrl + "/faqs";

        var response = client.portalInvite(user);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals("[Do Not Reply] HMCTS Pre-recorded Evidence Portal Invitation", response.getSubject());
        compareBody(
            """
            Hello John,

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

            [PRE FAQs - External.pdf](http://localhost:8080/assets/files/faqs.pdf)""", response);
    }

    @DisplayName("Should send case pending closure email")
    @Test
    @SuppressWarnings("LineLength")
    void casePendingClosure() {
        var user = createUser();
        var forCase = createCase();
        var date = "2021-01-01";

        var response = client.casePendingClosure(user, forCase, date);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + caseReference + " access update",
            response.getSubject()
        );
        compareBody(
            """
            Dear John Doe,

            Case 123456 has been set to close on 2021-01-01. Once the case has been closed, access to recordings will be removed.

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
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + caseReference + " access update",
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
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Case reference " + caseReference + " access update",
            response.getSubject()
        );
        compareBody(
            """
            Dear John Doe,

            Case 123456 will no longer be closed and access to recordings will remain available.

            Kind regards,
            Pre-Recorded Evidence Team""", response);
    }

    @Test
    @DisplayName("Should send editing jointly agreed email")
    @SuppressWarnings("LineLength")
    void editingJointlyAgreed() {
        var user = createUser();
        var forEditRequest = createEditRequest();

        var response = client.editingJointlyAgreed(user.getEmail(), forEditRequest);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Edit request for case reference 123456",
            response.getSubject()
        );
        compareBody(
            """
            This is a notification that 1 edits have been requested for approval for the following recording:

            Court: Court Name
            Case reference: 123456
            Witness name: First
            Defendant name(s): First Last

            Edit 1:
            Start time: 00:00:00
            End time: 00:00:30
            Time Removed: 00:00:00
            Reason:


            Edits have been jointly agreed: Yes

            PRE Portal link: [http://localhost:8080](http://localhost:8080)""", response);
    }

    @Test
    @DisplayName("Should send editing not jointly agreed email")
    @SuppressWarnings("LineLength")
    void editingNotJointlyAgreed() {
        var user = createUser();
        var forEditRequest = createEditRequest();

        var response = client.editingNotJointlyAgreed(user.getEmail(), forEditRequest);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Edit request for case reference 123456 (NOT JOINTLY AGREED)",
            response.getSubject()
        );
        compareBody(
            """
            This is a notification that 1 edits have been requested and may require a mention hearing for the following recording:

            Court: Court Name
            Case reference: 123456
            Witness name: First
            Defendant name(s): First Last

            Edit 1:
            Start time: 00:00:00
            End time: 00:00:30
            Time Removed: 00:00:00
            Reason:


            Edits have been jointly agreed: No

            PRE Portal link: [http://localhost:8080](http://localhost:8080)""", response);
    }

    @Test
    @DisplayName("Should send editing rejection email")
    @SuppressWarnings("LineLength")
    void editingRejectionEmail() {
        var user = createUser();
        var forEditRequest = createEditRequest();
        forEditRequest.setRejectionReason("REJECTION REASON");
        forEditRequest.setJointlyAgreed(true);

        var response = client.editingRejected(user.getEmail(), forEditRequest);
        assertEquals(fromEmailAddress, response.getFromEmail());
        assertEquals(
            "[Do Not Reply] Pre-recorded Evidence: Edit request REJECTION for case reference 123456",
            response.getSubject()
        );
        compareBody(
            """
            This is a notification that the edit request has been rejected:

            Rejection reason: REJECTION REASON

            Court: Court Name
            Case reference: 123456
            Witness name: First
            Defendant name(s): First Last

            Edit 1:
            Start time: 00:00:00
            End time: 00:00:30
            Time Removed: 00:00:00
            Reason:


            Edits have been jointly agreed: Yes

            PRE Portal link: [http://localhost:8080](http://localhost:8080)""", response);
    }
}
