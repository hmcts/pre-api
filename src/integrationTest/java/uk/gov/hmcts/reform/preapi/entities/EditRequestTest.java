package uk.gov.hmcts.reform.preapi.entities;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
public class EditRequestTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieveEditRequest() {
        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", null);
        entityManager.persist(court);

        var aCase = HelperFactory.createCase(court, "EXAMPLE123", false, null);
        entityManager.persist(aCase);

        var booking = HelperFactory.createBooking(aCase, Timestamp.from(Instant.now().plusSeconds(60 * 24)), null);
        entityManager.persist(booking);

        var captureSession = HelperFactory.createCaptureSession(
            booking,
            RecordingOrigin.PRE,
            null,
            null,
            null,
            null,
            null,
            null,
            RecordingStatus.RECORDING_AVAILABLE,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "index.mp4", null);
        entityManager.persist(recording);

        var user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        var editRequest = HelperFactory.createEditRequest(recording, "{}", EditRequestStatus.PENDING, user, null, null);
        entityManager.persist(editRequest);
        entityManager.flush();

        var retrievedEditRequest = entityManager.find(EditRequest.class, editRequest.getId());

        assertThat(retrievedEditRequest).isNotNull();
        assertThat(retrievedEditRequest.getId()).isEqualTo(editRequest.getId());
        assertThat(retrievedEditRequest.getSourceRecording().getId()).isEqualTo(recording.getId());
        assertThat(retrievedEditRequest.getStatus()).isEqualTo(EditRequestStatus.PENDING);
        assertThat(retrievedEditRequest.getCreatedBy().getId()).isEqualTo(user.getId());
    }
}
