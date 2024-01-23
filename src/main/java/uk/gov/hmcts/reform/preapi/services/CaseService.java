package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CaseService {

    private final CaseRepository caseRepository;

    private final CourtRepository courtRepository;

    private final ParticipantRepository participantRepository;

    private final BookingService bookingService;

    @Autowired
    public CaseService(CaseRepository caseRepository,
                       CourtRepository courtRepository,
                       ParticipantRepository participantRepository,
                       BookingService bookingService) {
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.bookingService = bookingService;
    }

    @Transactional
    public CaseDTO findById(UUID id) {
        return caseRepository
            .findByIdAndDeletedAtIsNull(id)
            .map(CaseDTO::new)
            .orElseThrow(() -> new NotFoundException("Case: " + id));
    }

    @Transactional
    public Page<CaseDTO> searchBy(String reference, UUID courtId, Pageable pageable) {
        return caseRepository
            .searchCasesBy(reference, courtId, pageable)
            .map(CaseDTO::new);
    }

    @Transactional
    public UpsertResult upsert(CreateCaseDTO createCaseDTO) {
        var foundCase = caseRepository.findById(createCaseDTO.getId());

        if (foundCase.isPresent() && foundCase.get().isDeleted()) {
            throw new ResourceInDeletedStateException("CaseDTO", createCaseDTO.getId().toString());
        }

        var isUpdate = foundCase.isPresent();
        if (!isCaseReferenceValid(isUpdate, createCaseDTO.getReference(), createCaseDTO.getId()))  {
            throw new ConflictException("Case reference is already in use");
        }

        var court = courtRepository.findById(createCaseDTO.getCourtId()).orElse(null);

        if (!isUpdate && court == null) {
            throw new NotFoundException("Court: " + createCaseDTO.getCourtId());
        }

        var newCase = foundCase.orElse(new Case());
        newCase.setId(createCaseDTO.getId());
        newCase.setCourt(court);
        if (createCaseDTO.getReference() != null) {
            newCase.setReference(createCaseDTO.getReference());
        }
        newCase.setTest(createCaseDTO.isTest());

        Set<Participant> oldParticipants = (newCase.getParticipants() == null || newCase.getParticipants().isEmpty())
            ? new HashSet<>()
            : new HashSet<>(Set.copyOf(newCase.getParticipants()));

        var newParticipants = Stream
            .ofNullable(createCaseDTO.getParticipants())
            .flatMap(participants -> participants.stream().map(model -> {
                var entity = participantRepository.findById(model.getId()).orElse(new Participant());

                if (entity.getDeletedAt() != null) {
                    throw new ResourceInDeletedStateException("Participant", entity.getId().toString());
                }

                entity.setId(model.getId());
                entity.setFirstName(model.getFirstName());
                entity.setLastName(model.getLastName());
                entity.setParticipantType(model.getParticipantType());
                entity.setCaseId(newCase);
                participantRepository.save(entity);
                return entity;
            }))
            .collect(Collectors.toSet());

        var ids = newParticipants.stream().map(Participant::getId).toList();
        oldParticipants
            .stream()
            .filter(p -> !ids.contains(p.getId()))
            .forEach(p -> {
                p.setCaseId(newCase);
                p.setDeletedAt(Timestamp.from(Instant.now()));
                participantRepository.save(p);
            });

        caseRepository.save(newCase);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID id) {
        var entity  = caseRepository.findByIdAndDeletedAtIsNull(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("CaseDTO: " + id);
        }
        bookingService.deleteCascade(entity.get());
        caseRepository.deleteById(id);
    }

    private boolean isCaseReferenceValid(boolean isUpdate, String caseReference, UUID caseId) {
        var foundCases = caseRepository.findAllByReference(caseReference);

        return isUpdate
            ? caseReference == null || foundCases.isEmpty() || foundCases.getFirst().getId().equals(caseId)
            : caseReference != null && foundCases.isEmpty();
    }
}
