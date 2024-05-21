package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.util.UUID;

@Repository
public interface ParticipantRepository extends SoftDeleteRepository<Participant, UUID> {
    boolean existsByIdAndCaseId_Id(UUID id, UUID caseId);
}
