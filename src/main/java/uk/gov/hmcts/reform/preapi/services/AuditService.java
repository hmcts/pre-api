package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateAuditDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.repositories.AuditRepository;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditRepository auditRepository;

    @Autowired
    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    public UpsertResult upsert(CreateAuditDTO createAuditDTO, UUID createdBy) {
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
}
