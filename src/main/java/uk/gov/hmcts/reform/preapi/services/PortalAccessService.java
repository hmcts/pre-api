package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class PortalAccessService {

    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public PortalAccessService(PortalAccessRepository portalAccessRepository) {
        this.portalAccessRepository = portalAccessRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UpsertResult update(CreatePortalAccessDTO createDto) {
        PortalAccess entity = portalAccessRepository
            .findByIdAndDeletedAtIsNull(createDto.getId())
            .orElseThrow(() -> new NotFoundException("PortalAccess: " + createDto.getId()));

        entity.setLastAccess(createDto.getLastAccess());
        entity.setStatus(getNewStatus(createDto));
        entity.setInvitedAt(createDto.getInvitedAt());
        entity.setRegisteredAt(createDto.getRegisteredAt());
        portalAccessRepository.save(entity);

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

    @Transactional
    public void deleteByUserId(UUID userId) {
        portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .ifPresent(access -> {
                access.setStatus(AccessStatus.INACTIVE);
                access.setDeletedAt(Timestamp.from(Instant.now()));
                portalAccessRepository.save(access);
            });
    }

    @Transactional
    public void undeleteByUserId(UUID userId) {
        portalAccessRepository
            .findAllByUser_IdAndDeletedAtIsNotNull(userId)
            .forEach(p -> {
                p.setDeletedAt(null);
                portalAccessRepository.save(p);
            });
    }

    public Boolean exists(UUID id) {
        return portalAccessRepository.existsById(id);
    }

    public Boolean isNotDeletedPortalUser(UUID userId) {
        return portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .isPresent();
    }

    public void upsertPortalAccessEntity(UUID id,
                                         User userEntity,
                                         AccessStatus accessStatus,
                                         Timestamp timestamp) {
        PortalAccess portalAccessEntity = portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(id)
            .orElse(new PortalAccess());

        portalAccessEntity.setUser(userEntity);
        portalAccessEntity.setStatus(accessStatus);
        portalAccessEntity.setInvitedAt(timestamp);
        portalAccessRepository.save(portalAccessEntity);
    }

    private AccessStatus getNewStatus(CreatePortalAccessDTO dto) {
        if (dto.getStatus() == AccessStatus.ACTIVE && dto.getRegisteredAt() == null) {
            return AccessStatus.INVITATION_SENT;
        }
        return dto.getStatus();
    }
}
