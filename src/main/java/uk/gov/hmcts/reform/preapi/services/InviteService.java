package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.util.UUID;

@Slf4j
@Service
public class InviteService {
    private final UserService userService;
    private final PortalAccessRepository portalAccessRepository;
    private final EmailServiceFactory emailServiceFactory;
    private final UserRepository userRepository;

    @Autowired
    public InviteService(UserService userService, PortalAccessRepository portalAccessRepository,
                         EmailServiceFactory emailServiceFactory, UserRepository userRepository) {
        this.userService = userService;
        this.portalAccessRepository = portalAccessRepository;
        this.emailServiceFactory = emailServiceFactory;
        this.userRepository = userRepository;
    }

    @Transactional
    public InviteDTO findByUserId(UUID userId) {
        return portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(userId, AccessStatus.INVITATION_SENT)
            .map(InviteDTO::new)
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }

    @Transactional
    public Page<InviteDTO> findAllBy(
        String firstName,
        String lastName,
        String email,
        String organisation,
        AccessStatus accessStatus,
        Pageable pageable
    ) {
        return portalAccessRepository
            .findAllBy(firstName, lastName, email, organisation, accessStatus, pageable)
            .map(InviteDTO::new);
    }

    @Transactional
    public UpsertResult upsert(CreateInviteDTO createInviteDTO) {
        var result = userService.upsert(createInviteDTO);

        var user = userRepository.findById(createInviteDTO.getUserId())
            .orElseThrow(() -> new NotFoundException("User: " + createInviteDTO.getUserId()));
        onUserInvitedToPortal(user);

        return result;
    }

    public UpsertResult redeemInvite(String email) {
        var portalAccess = portalAccessRepository
            .findByUser_EmailIgnoreCaseAndDeletedAtNullAndUser_DeletedAtNull(email)
            .orElseThrow(() -> new NotFoundException("Invite: " + email));
        portalAccess.setStatus(AccessStatus.ACTIVE);
        portalAccess.setRegisteredAt(Timestamp.from(java.time.Instant.now()));
        portalAccessRepository.save(portalAccess);
        return UpsertResult.UPDATED;
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        var portalAccess = portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndStatus(userId, AccessStatus.INVITATION_SENT)
            .orElseThrow(() -> new NotFoundException("User: " + userId));

        userService.deleteById(portalAccess.getUser().getId());
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onUserInvitedToPortal(User u) {
        log.info("onUserInvitedToPortal: User({})", u.getId());

        try {
            if (!emailServiceFactory.isEnabled()) {
                return;
            }
            emailServiceFactory.getEnabledEmailService().portalInvite(u);
        } catch (Exception e) {
            log.error("Failed to notify user of invite to portal: " + u.getId());
        }
    }
}
