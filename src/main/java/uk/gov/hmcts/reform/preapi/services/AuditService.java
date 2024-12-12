package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ImmutableDataException;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;

import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    @Autowired
    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
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
    public Page<CreateAuditDTO> findAll(Pageable pageable) {
        return auditRepository
            .searchAll(pageable)
            .map(CreateAuditDTO::new);
    }

    public List<Audit> getAuditsByTableRecordId(UUID tableRecordId) {
        return auditRepository.findByTableRecordId(tableRecordId);
    }
}
