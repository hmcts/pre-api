package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.InviteDTO;
import uk.gov.hmcts.reform.preapi.entities.Invite;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.InviteRepository;

import java.util.UUID;

@Service
public class InviteService {

    private final InviteRepository inviteRepository;

    @Autowired
    public InviteService(InviteRepository inviteRepository) {
        this.inviteRepository = inviteRepository;
    }

    @Transactional
    public InviteDTO findById(UUID id) {
        return inviteRepository
            .findById(id)
            .map(InviteDTO::new)
            .orElseThrow(() -> new NotFoundException("Invite: " + id));
    }

    @Transactional
    public Page<InviteDTO> findAllBy(
        String firstName,
        String lastName,
        String email,
        String organisation,
        Pageable pageable
    ) {
        return inviteRepository
            .searchBy(firstName, lastName, email, organisation, pageable)
            .map(InviteDTO::new);
    }

    @Transactional
    public UpsertResult upsert(CreateInviteDTO createInviteDTO) {
        final var foundInvite = inviteRepository.findById(createInviteDTO.getId());
        final var isUpdate = foundInvite.isPresent();

        if (isUpdate) {
            throw new ConflictException("InviteDTO: " + createInviteDTO.getId());
        } else {
            if (inviteRepository.existsByEmail(createInviteDTO.getEmail())) {
                throw new ConflictException("InviteDTO: " + createInviteDTO.getId());
            }
            if (inviteRepository.existsByCode(createInviteDTO.getCode())) {
                throw new ConflictException("InviteDTO: " + createInviteDTO.getId());
            }
        }

        var newInvite = foundInvite.orElse(new Invite());
        newInvite.setId(createInviteDTO.getId());
        newInvite.setFirstName(createInviteDTO.getFirstName());
        newInvite.setLastName(createInviteDTO.getLastName());
        newInvite.setEmail(createInviteDTO.getEmail());
        newInvite.setOrganisation(createInviteDTO.getOrganisation());
        newInvite.setPhone(createInviteDTO.getPhone());
        newInvite.setCode(createInviteDTO.getCode());
        inviteRepository.save(newInvite);

        return UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID id) {
        if (!inviteRepository.existsById(id)) {
            throw new NotFoundException("InviteDTO: " + id);
        }
        inviteRepository.deleteById(id);
    }
}
