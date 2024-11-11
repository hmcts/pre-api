package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.applicationinsights.TelemetryClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.Map;

@Slf4j
@Component
public class PerformEditRequest extends RobotUserTask {

    private final EditRequestService editRequestService;
    private final TelemetryClient telemetryClient;

    @Autowired
    public PerformEditRequest(EditRequestService editRequestService,
                              UserService userService,
                              UserAuthenticationService userAuthenticationService,
                              TelemetryClient telemetryClient,
                              @Value("${cron-user-email}") String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.editRequestService = editRequestService;
        this.telemetryClient = telemetryClient;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running PerformEditRequest task");

        // claims the oldest existing pending request and performs edit
        editRequestService.getNextPendingEditRequest()
            .ifPresentOrElse(
                this::attemptPerformEditRequest,
                () -> log.info("No pending edit requests found"));
    }

    private void attemptPerformEditRequest(EditRequest editRequest) {
        log.info("Attempting to perform EditRequest {}", editRequest.getId());
        try {
            editRequestService.updateEditRequestStatus(editRequest.getId(), EditRequestStatus.PROCESSING);
            editRequestService.performEdit(editRequest.getId());
            telemetryClient.trackEvent("PerformEditRequest Success",
                                       Map.of("editRequestId", editRequest.getId().toString()),
                                       Map.of());
        } catch (PessimisticLockingFailureException | ResourceInWrongStateException e) {
            // edit request is locked or has already been updated to a different state so it is skipped
            log.info("Skipping EditRequest {}, already reserved by another process", editRequest.getId());
        } catch (InterruptedException e) {
            log.error("Error while performing EditRequest {}", editRequest.getId(), e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Error while performing EditRequest {}", editRequest.getId(), e);
            telemetryClient.trackEvent("PerformEditRequest Failure",
                                       Map.of(
                                           "editRequestId", editRequest.getId().toString(),
                                           "message", e.getMessage()
                                       ), Map.of());
        }
    }
}
