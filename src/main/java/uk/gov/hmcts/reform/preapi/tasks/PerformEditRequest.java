package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.entities.EditRequest;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.UserService;

@Slf4j
@Component
public class PerformEditRequest extends RobotUserTask {

    private final EditRequestService editRequestService;

    @Autowired
    public PerformEditRequest(EditRequestService editRequestService,
                              UserService userService,
                              UserAuthenticationService userAuthenticationService,
                              @Value("${cron-user-email}") String cronUserEmail) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.editRequestService = editRequestService;
    }

    @Override
    public void run() {
        signInRobotUser();
        log.info("Running PerformEditRequest task");

        editRequestService.getPendingEditRequests()
            .forEach(this::attemptPerformEditRequest);
    }

    private void attemptPerformEditRequest(EditRequest editRequest) {
        log.info("Attempting to perform EditRequest {}", editRequest.getId());
        try {
            editRequestService.performEdit(editRequest.getId());
            // todo generate asset (including create recording entity)
            // todo copy edit instructions to recording (?)
        } catch (PessimisticLockingFailureException | ResourceInWrongStateException e) {
            // edit request is locked or has already been updated to a different state so it is skipped
            log.info("Skipping EditRequest {}, already reserved by another process", editRequest.getId());
        } catch (Exception e) {
            log.error("Error while performing EditRequest {}", editRequest.getId(), e);
        }
    }
}