package uk.gov.hmcts.reform.preapi.dto;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;

import java.sql.Timestamp;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CreatePortalAccessDTOTest {
    @Test
    void constructorPortalAccessDto() {
        PortalAccessDTO portalAccessDTO = new PortalAccessDTO();
        portalAccessDTO.setId(UUID.randomUUID());
        portalAccessDTO.setLastAccess(new Timestamp(System.currentTimeMillis() - 100000));
        portalAccessDTO.setStatus(AccessStatus.ACTIVE);
        portalAccessDTO.setInvitedAt(new Timestamp(System.currentTimeMillis() - 200000));
        portalAccessDTO.setRegisteredAt(new Timestamp(System.currentTimeMillis() - 300000));

        CreatePortalAccessDTO createDTO = new CreatePortalAccessDTO(portalAccessDTO);

        assertEquals(portalAccessDTO.getId(), createDTO.getId());
        assertEquals(portalAccessDTO.getLastAccess(), createDTO.getLastAccess());
        assertEquals(portalAccessDTO.getStatus(), createDTO.getStatus());
        assertEquals(portalAccessDTO.getInvitedAt(), createDTO.getInvitedAt());
        assertEquals(portalAccessDTO.getRegisteredAt(), createDTO.getRegisteredAt());
    }
}
