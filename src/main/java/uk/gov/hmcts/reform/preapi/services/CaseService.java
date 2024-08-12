package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    private final ShareBookingService shareBookingService;

    @Autowired
    public CaseService(CaseRepository caseRepository,
                       CourtRepository courtRepository,
                       ParticipantRepository participantRepository,
                       BookingService bookingService,
                       ShareBookingService shareBookingService) {
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.bookingService = bookingService;
        this.shareBookingService = shareBookingService;
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasCaseAccess(authentication, #id)")
    public CaseDTO findById(UUID id) {
        return caseRepository
            .findByIdAndDeletedAtIsNull(id)
            .map(CaseDTO::new)
            .orElseThrow(() -> new NotFoundException("Case: " + id));
    }

    @Transactional
    @PreAuthorize("!#includeDeleted or @authorisationService.canViewDeleted(authentication)")
    public Page<CaseDTO> searchBy(String reference, UUID courtId, boolean includeDeleted, Pageable pageable) {
        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        var authorisedCourt = auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId();

        return caseRepository
            .searchCasesBy(
                reference,
                courtId,
                includeDeleted,
                authorisedCourt,
                pageable
            )
            .map(CaseDTO::new);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createCaseDTO)")
    public UpsertResult upsert(CreateCaseDTO createCaseDTO) {
        var foundCase = caseRepository.findById(createCaseDTO.getId());
        var isUpdate = foundCase.isPresent();

        if (isUpdate) {
            if (foundCase.get().isDeleted()) {
                throw new ResourceInDeletedStateException("CaseDTO", createCaseDTO.getId().toString());
            }
            if (foundCase.get().getState() != CaseState.OPEN
                && foundCase.get().getState() == createCaseDTO.getState()
            ) {
                throw new ResourceInWrongStateException(
                    "Resource Case("
                        + createCaseDTO.getId()
                        + ") is in state "
                        + foundCase.get().getState()
                        + ". Cannot update case unless in state OPEN.");
            }
        }

        if (!isCaseReferenceValid(isUpdate, createCaseDTO))  {
            throw new ConflictException("Case reference is already in use for this court");
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

        // if closing case then trigger deletion of shares
        if (isUpdate && createCaseDTO.getState() == CaseState.CLOSED && newCase.getState() != CaseState.CLOSED) {
            shareBookingService.deleteCascade(newCase);
        }

        if (!isUpdate) {
            newCase.setCreatedAt(Timestamp.from(Instant.now()));
        }

        // todo update once CreateCaseDTO.state is made not nullable (currently breaking)
        newCase.setState(createCaseDTO.getState() == null ? CaseState.OPEN : createCaseDTO.getState());
        newCase.setClosedAt(createCaseDTO.getClosedAt());
        caseRepository.saveAndFlush(newCase);

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
    @PreAuthorize("@authorisationService.hasCaseAccess(authentication, #id)")
    public void deleteById(UUID id) {
        var caseEntity = caseRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("CaseDTO: " + id));
        caseEntity.setDeleteOperation(true);
        bookingService.deleteCascade(caseEntity);
        caseEntity.setDeletedAt(Timestamp.from(Instant.now()));
        caseRepository.saveAndFlush(caseEntity);
    }

    @Transactional
    public void undelete(UUID id) {
        var entity = caseRepository.findById(id).orElseThrow(() -> new NotFoundException("Case: " + id));
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        caseRepository.save(entity);
    }

    private boolean isCaseReferenceValid(boolean isUpdate, CreateCaseDTO createCaseDTO) {
        var foundCases = caseRepository
            .findAllByReferenceAndCourt_Id(createCaseDTO.getReference(), createCaseDTO.getCourtId());

        return isUpdate
            ? createCaseDTO.getReference() == null
                || foundCases.isEmpty()
                || foundCases.getFirst().getId().equals(createCaseDTO.getId())
            : createCaseDTO.getReference() != null
                && foundCases.isEmpty();
    }

    @Transactional
    public void closePendingCases() {
        var timestamp = Timestamp.from(Instant.now().minusSeconds(29L * 24 * 60 * 60));
        caseRepository.findAllByStateAndClosedAtBefore(CaseState.PENDING_CLOSURE, timestamp).forEach(c -> {
            c.setState(CaseState.CLOSED);
            caseRepository.save(c);
        });
    }
}
