package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

import java.sql.Timestamp;
import java.util.UUID;

@Service
public class InviteService {
    private final UserService userService;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public InviteService(UserService userService, PortalAccessRepository portalAccessRepository) {
        this.userService = userService;
        this.portalAccessRepository = portalAccessRepository;
    }

    @Transactional
    public InviteDTO findByUserId(UUID userId) {
        return portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndInvitedAtIsNotNull(userId)
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
        return userService.upsert(createInviteDTO);
    }

    public UpsertResult redeemInvite(String email) {
        var portalAccess = portalAccessRepository
            .findByUser_EmailAndDeletedAtNullAndUser_DeletedAtNull(email)
            .orElseThrow(() -> new NotFoundException("Invite: " + email));
        portalAccess.setStatus(AccessStatus.ACTIVE);
        portalAccess.setRegisteredAt(Timestamp.from(java.time.Instant.now()));
        portalAccessRepository.save(portalAccess);
        return UpsertResult.UPDATED;
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        var portalAccess = portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNullAndInvitedAtIsNotNull(userId)
            .orElseThrow(() -> new NotFoundException("User: " + userId));
        var user = portalAccess.getUser();

        userService.deleteById(user.getId());
    }
}
