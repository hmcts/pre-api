package uk.gov.hmcts.reform.preapi.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.preapi.entities.Room;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomRepository extends JpaRepository<Room, UUID> {
    @Query(
        """
        SELECT r FROM Room r
        INNER JOIN r.courts courts
        WHERE (CAST(:courtId as uuid) IS NULL OR courts.id = :courtId)
        """
    )
    List<Room> searchAllBy(@Param("courtId") UUID courtId);

}
