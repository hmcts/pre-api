package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.entities.Invite;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.InviteRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = InviteService.class)
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.TooManyMethods"})
class InviteServiceTest {

    private static Invite inviteEntity;

    private static List<Invite> allInviteEntities = new ArrayList<>();

    @MockBean
    private InviteRepository inviteRepository;

    @Autowired
    private InviteService inviteService;

    @BeforeAll
    static void setUp() {
        inviteEntity = new Invite();
        inviteEntity.setId(UUID.randomUUID());
        inviteEntity.setFirstName("Firstname");
        inviteEntity.setLastName("Lastname");
        inviteEntity.setEmail("example@example.com");
        inviteEntity.setOrganisation("Organisation");
        inviteEntity.setPhone("0123456789");
        inviteEntity.setCode("ABCDE");
        inviteEntity.setCreatedAt(Timestamp.from(Instant.now()));
        inviteEntity.setModifiedAt(Timestamp.from(Instant.now()));

        allInviteEntities.add(inviteEntity);
    }

    @DisplayName("Find a invite by it's id and return a model")
    @Test
    void findInviteByIdSuccess() {
        when(inviteRepository.findById(inviteEntity.getId())).thenReturn(Optional.ofNullable(inviteEntity));

        var model = inviteService.findById(inviteEntity.getId());
        assertThat(model.getId()).isEqualTo(inviteEntity.getId());
        assertThat(model.getFirstName()).isEqualTo(inviteEntity.getFirstName());
        assertThat(model.getLastName()).isEqualTo(inviteEntity.getLastName());
        assertThat(model.getEmail()).isEqualTo(inviteEntity.getEmail());
        assertThat(model.getOrganisation()).isEqualTo(inviteEntity.getOrganisation());
        assertThat(model.getPhone()).isEqualTo(inviteEntity.getPhone());
        assertThat(model.getCode()).isEqualTo(inviteEntity.getCode());
    }

    @DisplayName("Find a invite by it's id which does not exist")
    @Test
    void findInviteByIdNotFound() {
        var randomId = UUID.randomUUID();
        when(inviteRepository.findById(randomId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> inviteService.findById(randomId));
    }

    @DisplayName("Find all invites and return a list of models")
    @Test
    void findAllSuccess() {
        when(inviteRepository.searchBy(null, null, null, null, null))
            .thenReturn(new PageImpl<>(allInviteEntities));

        Page<InviteDTO> models = inviteService.findAllBy(null, null, null, null,
                                                         null);
        assertThat(models.getTotalElements()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(inviteEntity.getId());
        assertThat(models.get().toList().getFirst().getFirstName()).isEqualTo(inviteEntity.getFirstName());
        assertThat(models.get().toList().getFirst().getLastName()).isEqualTo(inviteEntity.getLastName());
        assertThat(models.get().toList().getFirst().getEmail()).isEqualTo(inviteEntity.getEmail());
        assertThat(models.get().toList().getFirst().getOrganisation()).isEqualTo(inviteEntity.getOrganisation());
        assertThat(models.get().toList().getFirst().getPhone()).isEqualTo(inviteEntity.getPhone());
        assertThat(models.get().toList().getFirst().getCode()).isEqualTo(inviteEntity.getCode());
    }

    @Test
    void createSuccess() {
        Invite testingInvite = createTestingInvite();
        var inviteDTOModel = new CreateInviteDTO(testingInvite);

        when(inviteRepository.findById(testingInvite.getId())).thenReturn(Optional.empty());

        inviteService.upsert(inviteDTOModel);

        verify(inviteRepository, times(1)).findById(inviteDTOModel.getId());
        verify(inviteRepository, times(1)).save(any(Invite.class));
    }

    @Test
    void updateDenied() {
        Invite testingInvite = createTestingInvite();
        var inviteDTOModel = new CreateInviteDTO(testingInvite);

        when(inviteRepository.findById(testingInvite.getId())).thenReturn(Optional.of(inviteEntity));

        assertThrows(ConflictException.class, () -> inviteService.upsert(inviteDTOModel));

        verify(inviteRepository, times(1)).findById(inviteDTOModel.getId());
    }

    Invite createTestingInvite() {
        var testInvite = new Invite();
        testInvite.setId(UUID.randomUUID());
        testInvite.setFirstName("Firstname");
        testInvite.setLastName("Lastname");
        testInvite.setEmail("example@example.com");
        testInvite.setOrganisation("Organisation");
        testInvite.setPhone("0123456789");
        testInvite.setCreatedAt(Timestamp.from(Instant.now()));
        testInvite.setModifiedAt(Timestamp.from(Instant.now()));
        return testInvite;
    }


    @Test
    void deleteByIdSuccess() {
        when(inviteRepository.existsById(inviteEntity.getId())).thenReturn(true);

        inviteService.deleteById(inviteEntity.getId());

        verify(inviteRepository, times(1)).existsById(inviteEntity.getId());
        verify(inviteRepository, times(1)).deleteById(inviteEntity.getId());
    }

    @Test
    void deleteByIdNotFound() {
        UUID inviteId = UUID.randomUUID();
        when(inviteRepository.existsById(inviteId)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> inviteService.deleteById(inviteId));

        verify(inviteRepository, times(1)).existsById(inviteId);
        verify(inviteRepository, never()).deleteById(inviteId);
    }
}
