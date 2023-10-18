package uk.gov.hmcts.reform.preapi.audit.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Audit;

import java.util.UUID;

@Repository
public interface AuditRepository extends JpaRepository<Audit, UUID> {

}
