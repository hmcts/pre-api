package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SuppressWarnings("PMD.SingularField")
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParticipantRepository participantRepository;
    private final CaseRepository caseRepository;
    private final CaptureSessionService captureSessionService;
    private final ShareBookingService shareBookingService;
    private final CaseService caseService;

    @Autowired
    public BookingService(final BookingRepository bookingRepository,
                          final CaseRepository caseRepository,
                          final ParticipantRepository participantRepository,
                          final CaptureSessionService captureSessionService,
                          final ShareBookingService shareBookingService,
                          @Lazy CaseService caseService) {
        this.bookingRepository = bookingRepository;
        this.participantRepository = participantRepository;
        this.caseRepository = caseRepository;
        this.captureSessionService = captureSessionService;
        this.shareBookingService = shareBookingService;
        this.caseService = caseService;
    }

    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #id)")
    @Transactional
    public BookingDTO findById(UUID id) {
        return bookingRepository.findByIdAndDeletedAtIsNull(id)
            .map(BookingDTO::new)
            .orElseThrow(() -> new NotFoundException("BookingDTO not found"));
    }

    @Transactional
    public Page<BookingDTO> findAllByCaseId(UUID caseId, Pageable pageable) {
        return bookingRepository.findByCaseId_IdAndDeletedAtIsNull(caseId, pageable)
            .map(BookingDTO::new);
    }

    @Transactional
    public Page<BookingDTO> searchBy(
        UUID caseId,
        String caseReference,
        UUID courtId,
        Optional<Timestamp> scheduledFor,
        UUID participantId,
        Boolean hasRecordings,
        List<RecordingStatus> statuses,
        List<RecordingStatus> notStatuses,
        Pageable pageable
    ) {
        var until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(
                t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        var authorisedBookings = auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings();
        var authorisedCourt = auth.isPortalUser() || auth.isAdmin() ? null : auth.getCourtId();

        return bookingRepository
            .searchBookingsBy(
                caseId,
                caseReference,
                courtId,
                scheduledFor.orElse(null),
                until, // 11:59:59 PM
                participantId,
                authorisedBookings,
                authorisedCourt,
                hasRecordings,
                statuses,
                notStatuses,
                pageable
            )
            .map(BookingDTO::new);
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    @Transactional
    @PreAuthorize("@authorisationService.hasUpsertAccess(authentication, #createBookingDTO)")
    public UpsertResult upsert(CreateBookingDTO createBookingDTO) {

        if (bookingAlreadyDeleted(createBookingDTO.getId())) {
            throw new ResourceInDeletedStateException("BookingDTO", createBookingDTO.getId().toString());
        }

        var optBooking = bookingRepository.findById(createBookingDTO.getId());
        var bookingEntity = optBooking.orElse(new Booking());

        var caseEntity = caseRepository.findByIdAndDeletedAtIsNull(createBookingDTO.getCaseId())
            .orElseThrow(() -> new NotFoundException("Case: " + createBookingDTO.getCaseId()));

        if (caseEntity.getState() != CaseState.OPEN) {
            throw new ResourceInWrongStateException(
                "Booking",
                createBookingDTO.getId(),
                caseEntity.getState(),
                "OPEN"
            );
        }

        createBookingDTO.getParticipants().forEach(p -> {
            if (!participantRepository.existsByIdAndCaseId_Id(p.getId(), createBookingDTO.getCaseId())) {
                throw new NotFoundException("Participant: " + p.getId() + " in case: " + createBookingDTO.getCaseId());
            }
        });

        bookingEntity.setId(createBookingDTO.getId());
        bookingEntity.setCaseId(caseEntity);
        bookingEntity.setParticipants(
            Stream.ofNullable(createBookingDTO.getParticipants())
                .flatMap(participants -> participants.stream().map(model -> {
                    var entity = participantRepository.findById(model.getId()).orElse(new Participant());

                    if (entity.getDeletedAt() != null) {
                        throw new ResourceInDeletedStateException("Participant", entity.getId().toString());
                    }
                    entity.setId(model.getId());
                    entity.setFirstName(model.getFirstName());
                    entity.setLastName(model.getLastName());
                    entity.setParticipantType(model.getParticipantType());
                    entity.setCaseId(caseEntity);
                    participantRepository.save(entity);
                    return entity;
                }))
                .collect(Collectors.toSet()));
        bookingEntity.setScheduledFor(createBookingDTO.getScheduledFor());

        bookingRepository.save(bookingEntity);

        var isUpdate = optBooking.isPresent();
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    private boolean bookingAlreadyDeleted(UUID id) {
        return bookingRepository.existsByIdAndDeletedAtIsNotNull(id);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #id)")
    public void markAsDeleted(UUID id) {
        var booking = bookingRepository
            .findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("Booking: " + id));
        captureSessionService.deleteCascade(booking);
        shareBookingService.deleteCascade(booking);
        booking.setDeleteOperation(true);
        booking.setDeletedAt(Timestamp.from(Instant.now()));
        bookingRepository.saveAndFlush(booking);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #id)")
    public void undelete(UUID id) {
        var entity = bookingRepository.findById(id).orElseThrow(() -> new NotFoundException("Booking: " + id));
        caseService.undelete(entity.getCaseId().getId());
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        bookingRepository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteCascade(Case caseEntity) {
        bookingRepository
            .findAllByCaseIdAndDeletedAtIsNull(caseEntity)
            .forEach(booking -> {
                captureSessionService.deleteCascade(booking);
                shareBookingService.deleteCascade(booking);
                booking.setDeleteOperation(true);
                booking.setDeletedAt(Timestamp.from(Instant.now()));
                bookingRepository.save(booking);
            });
    }

    @Transactional
    public List<BookingDTO> findAllPastBookings() {
        return bookingRepository.findAllPastUnusedBookings(Timestamp.from(Instant.now()))
            .stream()
            .map(BookingDTO::new)
            .toList();
    }
}
