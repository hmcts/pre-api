package uk.gov.hmcts.reform.preapi.services;

import javax.swing.text.html.Option;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = PortalAccessService.class)
public class PortalAccessServiceTest {
    @MockBean
    private PortalAccessRepository portalAccessRepository;

    @Autowired
    private PortalAccessService portalAccessService;

    @DisplayName("Update a portal access entity")
    @Test
    public void updateSuccess() {
        var model = new CreatePortalAccessDTO();
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));

        var entity = new PortalAccess();
        entity.setId(model.getId());

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.of(entity));

        assertThat(portalAccessService.update(model)).isEqualTo(UpsertResult.UPDATED);

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, times(1)).save(any());
    }

    @DisplayName("Update a portal access entity when portal access does not exist")
    @Test
    public void updateNotFound() {
        var model = new CreatePortalAccessDTO();
        model.setId(UUID.randomUUID());
        model.setStatus(AccessStatus.ACTIVE);
        model.setLastAccess(Timestamp.from(Instant.now()));
        model.setInvitedAt(Timestamp.from(Instant.now()));

        when(portalAccessRepository.findByIdAndDeletedAtIsNull(model.getId()))
            .thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> portalAccessService.update(model)
        ).getMessage();
        assertThat(message).isEqualTo("Not found: PortalAccess: " + model.getId());

        verify(portalAccessRepository, times(1)).findByIdAndDeletedAtIsNull(model.getId());
        verify(portalAccessRepository, never()).save(any());
    }

}
