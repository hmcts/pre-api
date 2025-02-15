package uk.gov.hmcts.reform.preapi.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
import uk.gov.hmcts.reform.preapi.email.CaseStateChangeNotifierFlowClient;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
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
    private final CaseStateChangeNotifierFlowClient caseStateChangeNotifierFlowClient;
    private final BookingRepository bookingRepository;
    private final EmailServiceFactory emailServiceFactory;

    @Autowired
    public CaseService(CaseRepository caseRepository,
                       CourtRepository courtRepository,
                       ParticipantRepository participantRepository,
                       BookingService bookingService,
                       ShareBookingService shareBookingService,
                       CaseStateChangeNotifierFlowClient caseStateChangeNotifierFlowClient,
                       @Lazy BookingRepository bookingRepository,
                       EmailServiceFactory emailServiceFactory
    ) {
        this.caseRepository = caseRepository;
        this.courtRepository = courtRepository;
        this.participantRepository = participantRepository;
        this.bookingService = bookingService;
        this.shareBookingService = shareBookingService;
        this.caseStateChangeNotifierFlowClient = caseStateChangeNotifierFlowClient;
        this.bookingRepository = bookingRepository;
        this.emailServiceFactory = emailServiceFactory;
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

            if ((isCasePendingClosure || isCaseClosure) && bookingRepository
                .findAllByCaseIdAndDeletedAtIsNull(foundCase.get())
                .stream()
                .anyMatch(b -> b.getCaptureSessions().isEmpty()
                    || b.getCaptureSessions()
                    .stream()
                    .map(CaptureSession::getStatus)
                    .anyMatch(s -> s != RecordingStatus.FAILURE
                        && s != RecordingStatus.NO_RECORDING
                        && s != RecordingStatus.RECORDING_AVAILABLE)
                )
            ) {
                throw new ResourceInWrongStateException(
                    "Resource Case("
                        + createCaseDTO.getId()
                        + ") has open bookings which must not be present when updating state to "
                        + createCaseDTO.getState());
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
        var timestamp = Timestamp.from(Instant.now());
        caseRepository.findAllByStateAndClosedAtBefore(CaseState.PENDING_CLOSURE, timestamp).forEach(c -> {
            c.setState(CaseState.CLOSED);
            caseRepository.save(c);
            onCaseClosed(c);
        });
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCaseClosed(Case c) {
        log.info("onCaseClosed: Case({})", c.getId());
        var shares = shareBookingService.deleteCascade(c);

        try {
            if (!emailServiceFactory.isEnabled()) {
                caseStateChangeNotifierFlowClient.emailAfterCaseStateChange(
                    shares
                        .stream()
                        .map(share -> new CaseStateChangeNotificationDTO(EmailType.CLOSED, c, share))
                        .toList());
            } else {
                var emailService = emailServiceFactory.getEnabledEmailService();
                shares.forEach(share -> emailService.caseClosed(share.getSharedWith(), c));
            }
        } catch (Exception e) {
            log.error("Failed to notify users of case closure: {}", c.getId());
        }

        bookingRepository
            .findAllByCaseIdAndDeletedAtIsNull(c)
            .stream()
            .filter(b -> !b.getCaptureSessions().isEmpty()
                && b.getCaptureSessions()
                .stream()
                .map(CaptureSession::getStatus)
                .anyMatch(s -> s == RecordingStatus.FAILURE || s == RecordingStatus.NO_RECORDING))
            .map(BaseEntity::getId)
            .forEach(bookingService::markAsDeleted);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCaseClosureCancellation(Case c) {
        log.info("onCaseClosureCancellation: Case({})", c.getId());
        var shares = shareBookingService.getSharesForCase(c);

        try {
            if (!emailServiceFactory.isEnabled()) {
                caseStateChangeNotifierFlowClient.emailAfterCaseStateChange(
                    shares
                        .stream()
                        .map(share -> new CaseStateChangeNotificationDTO(EmailType.CLOSURE_CANCELLATION, c, share))
                        .toList()
                );
            } else {
                var emailService = emailServiceFactory.getEnabledEmailService();
                shares.forEach(share -> emailService.caseClosureCancelled(share.getSharedWith(), c));
            }
        } catch (Exception e) {
            log.error("Failed to notify users of case closure cancellation: {}", c.getId());
        }
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void onCasePendingClosure(Case c) {
        log.info("onCasePendingClosure: Case({})", c.getId());
        var shares = shareBookingService.getSharesForCase(c);

        try {
            if (!emailServiceFactory.isEnabled()) {
                caseStateChangeNotifierFlowClient.emailAfterCaseStateChange(
                    shares
                        .stream()
                        .map(share -> new CaseStateChangeNotificationDTO(EmailType.PENDING_CLOSURE, c, share))
                        .toList()
                );
            } else {
                var emailService = emailServiceFactory.getEnabledEmailService();
                shares.forEach(share -> emailService.casePendingClosure(share.getSharedWith(), c,
                                                                        c.getClosedAt()));
            }
        } catch (Exception e) {
            log.error("Failed to notify users of case pending closure: {}", c.getId());
        }
    }
}
