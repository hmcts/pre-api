package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchEditRequests;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class EditRequestServiceIT extends IntegrationTestBase {

    @Autowired
    private EditRequestService editRequestService;

    @Test
    @Transactional
    public void searchEditRequests() {
        mockAdminUser();

        var court = HelperFactory.createCourt(CourtType.CROWN, "Example Court", "1234");
        entityManager.persist(court);

        var caseEntity = HelperFactory.createCase(court, "CASE12345", true, null);
        entityManager.persist(caseEntity);

        var booking = HelperFactory.createBooking(caseEntity, Timestamp.from(Instant.now()), null);
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
            null,
            null
        );
        entityManager.persist(captureSession);

        var recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        var user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);

        var editRequest = HelperFactory.createEditRequest(
            recording,
            "{}",
            EditRequestStatus.PENDING,
            user,
            null,
            null,
            null,
            null,
            null,
            null
        );
        entityManager.persist(editRequest);

        SearchEditRequests paramsForExistingEditRequest = new SearchEditRequests();
        paramsForExistingEditRequest.setSourceRecordingId(recording.getId());
        var requests1 = editRequestService.findAll(paramsForExistingEditRequest, Pageable.unpaged()).toList();
        assertThat(requests1).hasSize(1);
        assertThat(requests1.getFirst().getId()).isEqualTo(editRequest.getId());

        SearchEditRequests paramsForRandomRecording = new SearchEditRequests();
        paramsForRandomRecording.setSourceRecordingId(UUID.randomUUID());
        var requests2 = editRequestService.findAll(paramsForRandomRecording, Pageable.unpaged()).toList();
        assertThat(requests2).isEmpty();

        var requests3 = editRequestService.findAll(new SearchEditRequests(), Pageable.unpaged()).toList();
        assertThat(requests3).hasSize(1);
        assertThat(requests3.getFirst().getId()).isEqualTo(editRequest.getId());
    }
}
