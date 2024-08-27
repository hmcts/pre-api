package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.CaseStateChangeNotificationDTO;
import uk.gov.hmcts.reform.preapi.dto.flow.CaseStateChangeNotificationDTO.EmailType;
import uk.gov.hmcts.reform.preapi.email.FlowHttpClient;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.EmailNotifierException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class CaseService {

    private final CaseRepository caseRepository;
    private final CourtRepository courtRepository;
    private final ParticipantRepository participantRepository;
    private final BookingService bookingService;
    private final ShareBookingService shareBookingService;
    private final FlowHttpClient flowHttpClient;

    @Autowired
    public CaseService(CaseRepository caseRepository,
                       CourtRepository courtRepository,
                       ParticipantRepository participantRepository,
                       BookingService bookingService,
                       ShareBookingService shareBookingService,
                       FlowHttpClient flowHttpClient) {
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.bookingService = bookingService;
        this.shareBookingService = shareBookingService;
        this.flowHttpClient = flowHttpClient;
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

        var isCaseClosure = false;
        var isCaseClosureCancellation = false;
        var isCasePendingClosure = false;

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
            isCaseClosure = foundCase.get().getState() != CaseState.CLOSED
                && createCaseDTO.getState() == CaseState.CLOSED;
            isCaseClosureCancellation = foundCase.get().getState() == CaseState.PENDING_CLOSURE
                && createCaseDTO.getState() == CaseState.OPEN;
            isCasePendingClosure = foundCase.get().getState() == CaseState.OPEN
                && createCaseDTO.getState() == CaseState.PENDING_CLOSURE;
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

        // todo update once CreateCaseDTO.state is made not nullable (currently breaking)
        newCase.setState(createCaseDTO.getState() == null ? CaseState.OPEN : createCaseDTO.getState());
        newCase.setClosedAt(createCaseDTO.getClosedAt());

        // if closing case then trigger deletion of shares
        if (isCaseClosure) {
            onCaseClosed(newCase);
        } else if (isCaseClosureCancellation) {
            onCaseClosureCancellation(newCase);
        } else if (isCasePendingClosure) {
            onCasePendingClosure(newCase);
        }

        if (!isUpdate) {
            newCase.setCreatedAt(Timestamp.from(Instant.now()));
        }

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
            onCaseClosed(c);
        });
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCaseClosed(Case c) {
        log.info("onCaseClosed: Case({})", c.getId());
        var notifications = shareBookingService.deleteCascade(c)
            .stream()
            .map(share -> new CaseStateChangeNotificationDTO(EmailType.CLOSED, c, share))
            .toList();
        try {
            flowHttpClient.emailAfterCaseStateChange(notifications);
        } catch (Exception e) {
            log.error("Failed to notify users of case closure: " + c.getId());
            throw new EmailNotifierException("Failed to notify users of case closure: Case(" + c.getId() + ")");
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCaseClosureCancellation(Case c) {
        log.info("onCaseClosureCancellation: Case({})", c.getId());
        var notifications = shareBookingService.getSharesForCase(c)
            .stream()
            .map(share -> new CaseStateChangeNotificationDTO(EmailType.CLOSURE_CANCELLATION, c, share))
            .toList();
        try {
            flowHttpClient.emailAfterCaseStateChange(notifications);
        } catch (Exception e) {
            log.error("Failed to notify users of case closure cancellation: " + c.getId());
            throw new EmailNotifierException("Failed to notify users of case closure cancellation: Case("
                                                 + c.getId()
                                                 + ")");
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCasePendingClosure(Case c) {
        log.info("onCasePendingClosure: Case({})", c.getId());
        var notifications = shareBookingService.getSharesForCase(c)
            .stream()
            .map(share -> new CaseStateChangeNotificationDTO(EmailType.PENDING_CLOSURE, c, share))
            .toList();
        try {
            flowHttpClient.emailAfterCaseStateChange(notifications);
        } catch (Exception e) {
            log.error("Failed to notify users of case pending closure: " + c.getId());
            throw new EmailNotifierException("Failed to notify users of case pending closure: Case(" + c.getId() + ")");
        }
    }
}
