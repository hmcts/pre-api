package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.util.UUID;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, UUID> {
    @SuppressWarnings("PMD.MethodNamingConventions")
    boolean existsByIdAndCaseId_Id(UUID id, UUID caseId);
}
