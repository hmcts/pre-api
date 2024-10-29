package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceBroker;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
public class PortalAccessService {

    private final PortalAccessRepository portalAccessRepository;

    private final EmailServiceBroker emailServiceBroker;

    @Autowired
    public PortalAccessService(PortalAccessRepository portalAccessRepository, EmailServiceBroker emailServiceBroker) {
        this.portalAccessRepository = portalAccessRepository;
        this.emailServiceBroker = emailServiceBroker;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UpsertResult update(CreatePortalAccessDTO createDto) {
        var entity = portalAccessRepository
            .findByIdAndDeletedAtIsNull(createDto.getId())
            .orElseThrow(() -> new NotFoundException("PortalAccess: " + createDto.getId()));

        entity.setLastAccess(createDto.getLastAccess());
        entity.setStatus(createDto.getStatus());
        entity.setInvitedAt(createDto.getInvitedAt());
        entity.setRegisteredAt(createDto.getRegisteredAt());
        portalAccessRepository.save(entity);

        onUserInvitedToPortal(entity.getUser());

        return UpsertResult.UPDATED;
    }


    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteById(UUID portalId) {
        portalAccessRepository
            .findById(portalId)
            .ifPresent(
                access -> {
                    access.setStatus(AccessStatus.INACTIVE);
                    access.setDeletedAt(Timestamp.from(Instant.now()));
                    portalAccessRepository.save(access);
                });
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onUserInvitedToPortal(User u) {
        log.info("onUserInvitedToPortal: User({})", u.getId());

        try {
            if (!emailServiceBroker.enable) {
                return;
            } else {
                var emailService = emailServiceBroker.getEnabledEmailService();
                emailService.portalInvite(u);
            }
        } catch (Exception e) {
            log.error("Failed to notify user of invite to portal: " + u.getId());
        }
    }
}
