package uk.gov.hmcts.reform.preapi.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Invite;

import java.util.UUID;

@Repository
public interface InviteRepository extends JpaRepository<Invite, UUID> {
    @Query(
        """
        SELECT i FROM Invite i
        WHERE (:firstName IS NULL OR i.firstName ILIKE %:firstName%)
        AND (:lastName IS NULL OR i.lastName ILIKE %:lastName%)
        AND (:email IS NULL OR i.email ILIKE %:email%)
        AND (:organisation IS NULL OR i.organisation ILIKE %:organisation%)
        """
    )
    Page<Invite> searchBy(
        @Param("firstName") String firstName,
        @Param("lastName") String lastName,
        @Param("email") String email,
        @Param("organisation") String organisation,
        Pageable pageable
    );
}
