package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.AuditDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

@Service
public class AuditService {

    private final AuditRepository auditRepository;
    private final UserRepository userRepository;
    private final AppAccessRepository appAccessRepository;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public AuditService(AuditRepository auditRepository, UserRepository userRepository,
                        AppAccessRepository appAccessRepository, PortalAccessRepository portalAccessRepository) {
        this.auditRepository = auditRepository;
        this.userRepository = userRepository;
        this.appAccessRepository = appAccessRepository;
        this.portalAccessRepository = portalAccessRepository;
    }

    public UpsertResult upsert(CreateAuditDTO createAuditDTO, @Nullable UUID createdBy) {
        if (auditRepository.existsById(createAuditDTO.getId())) {
            throw new ImmutableDataException(createAuditDTO.getId().toString());
        }

        var audit = new Audit();
        audit.setId(createAuditDTO.getId());
        audit.setAuditDetails(createAuditDTO.getAuditDetails());
        audit.setActivity(createAuditDTO.getActivity());
        audit.setCategory(createAuditDTO.getCategory());
        audit.setFunctionalArea(createAuditDTO.getFunctionalArea());
        audit.setTableName(createAuditDTO.getTableName());
        audit.setTableRecordId(createAuditDTO.getTableRecordId());
        audit.setSource(createAuditDTO.getSource());
        audit.setCreatedBy(createdBy);

        auditRepository.save(audit);

        return UpsertResult.CREATED;
    }

    @Transactional
    public Page<AuditDTO> findAll(
        Timestamp after,
        Timestamp before,
        String functionalArea,
        AuditLogSource source,
        String userName,
        UUID courtId,
        String caseReference,
        Pageable pageable
    ) {
        return auditRepository
            .searchAll(after, before, functionalArea, source, userName, courtId, caseReference, pageable)
            .map(audit -> {
                var createdById = audit.getCreatedBy();
                if (createdById == null) {
                    return new AuditDTO(audit);
                }
                return appAccessRepository.findById(createdById)
                    .map(aa -> new AuditDTO(audit, aa.getUser()))
                    .orElseGet(() -> portalAccessRepository.findById(createdById)
                        .map(pa -> new AuditDTO(audit, pa.getUser()))
                        .orElseGet(() -> new AuditDTO(audit)));
            });
    }

    public List<Audit> getAuditsByTableRecordId(UUID tableRecordId) {
        return auditRepository.findByTableRecordId(tableRecordId);
    }
}
