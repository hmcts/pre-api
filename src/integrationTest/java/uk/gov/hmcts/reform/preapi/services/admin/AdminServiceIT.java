package uk.gov.hmcts.reform.preapi.services.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.admin.AdminRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AdminServiceIT extends IntegrationTestBase {

    @Autowired
    AdminService adminService;

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    protected EntityManager entityManager;

    @Test
    @Transactional
    public void shouldCheckUuidExists() {
        Timestamp now = new Timestamp(System.currentTimeMillis());
        //Put a user in the database to test UUID against
        User user = HelperFactory.createUser("Example", "One", "example1@example.com", null, null, null);
        entityManager.persist(user);
        Court court = HelperFactory.createCourt(CourtType.CROWN, "Test Court", "Test123");
        entityManager.persist(court);
        Case case1 = HelperFactory.createCase(court, "null", true, now);
        entityManager.persist(case1);
        Booking booking = HelperFactory.createBooking(case1, now, now);
        entityManager.persist(booking);
        CaptureSession captureSession = HelperFactory.createCaptureSession(
            booking, RecordingOrigin.PRE,
            null, null, null, null, null,
            null, null, null
        );
        entityManager.persist(captureSession);
        Recording recording = HelperFactory.createRecording(captureSession, null, 1, "filename", null);
        entityManager.persist(recording);

        entityManager.flush();
        UUID userGeneratedId = user.getId();
        UUID bookingGeneratedId = booking.getId();
        UUID caseGeneratedId = case1.getId();
        UUID courtGeneratedId = court.getId();
        UUID captureSessionGeneratedId = captureSession.getId();
        UUID recordingGeneratedId = recording.getId();

        assertThat(adminService.findUuidType(userGeneratedId)).isEqualTo(AdminService.UuidTableType.USER);
        assertThat(adminService.findUuidType(bookingGeneratedId)).isEqualTo(AdminService.UuidTableType.BOOKING);
        assertThat(adminService.findUuidType(caseGeneratedId)).isEqualTo(AdminService.UuidTableType.CASE);
        assertThat(adminService.findUuidType(courtGeneratedId)).isEqualTo(AdminService.UuidTableType.COURT);
        assertThat(adminService.findUuidType(captureSessionGeneratedId))
            .isEqualTo(AdminService.UuidTableType.CAPTURE_SESSION);
        assertThat(adminService.findUuidType(recordingGeneratedId)).isEqualTo(AdminService.UuidTableType.RECORDING);

    }

    @Test
    @Transactional
    public void shouldThrowExceptionWhenUuidNotFound() {

        UUID randomUuid = UUID.randomUUID();

        assertThatThrownBy(() -> adminService.findUuidType(randomUuid))
            .isInstanceOf(NotFoundException.class);

    }

    @Test
    @Transactional
    public void shouldThrowExceptionWhenUuidIsNotInRelevantTable() {

        Role role = HelperFactory.createRole("role");
        entityManager.persist(role);

        entityManager.flush();

        UUID roleGeneratedId = role.getId();

        assertThatThrownBy(() -> adminService.findUuidType(roleGeneratedId))
            .isInstanceOf(NotFoundException.class);

    }

}
