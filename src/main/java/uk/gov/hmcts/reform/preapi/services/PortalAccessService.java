package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

@Service
public class PortalAccessService {

    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public PortalAccessService(PortalAccessRepository portalAccessRepository) {
        this.portalAccessRepository = portalAccessRepository;
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public UpsertResult update(CreatePortalAccessDTO createDto) {
        var entity = portalAccessRepository
            .findByIdAndDeletedAtIsNull(createDto.getId())
            .orElseThrow(() -> new NotFoundException("PortalAccess: " + createDto.getId()));

        entity.setLastAccess(createDto.getLastAccess());
        entity.setInvitedAt(createDto.getInvitedAt());
        entity.setRegisteredAt(createDto.getRegisteredAt());
        portalAccessRepository.save(entity);

        return UpsertResult.UPDATED;
    }

}
