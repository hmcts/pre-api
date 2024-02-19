package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

@Service
@SuppressWarnings("PMD.SingularField")
public class BookingService {


    private final BookingRepository bookingRepository;
    private final ParticipantRepository participantRepository;
    private final CaseRepository caseRepository;
    private final CaptureSessionService captureSessionService;
    private final ShareBookingService shareBookingService;

    @Autowired
    public BookingService(final BookingRepository bookingRepository,
                          final CaseRepository caseRepository,
                          final ParticipantRepository participantRepository,
                          CaptureSessionService captureSessionService,
                          ShareBookingService shareBookingService) {
        this.bookingRepository = bookingRepository;
        this.participantRepository = participantRepository;
        this.caseRepository = caseRepository;
        this.captureSessionService = captureSessionService;
        this.shareBookingService = shareBookingService;
    }

    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #id)")
    public BookingDTO findById(UUID id) {

        return bookingRepository.findByIdAndDeletedAtIsNull(id)
            .map(BookingDTO::new)
            .orElseThrow(() -> new NotFoundException("BookingDTO not found"));
    }

    public Page<BookingDTO> findAllByCaseId(UUID caseId, Pageable pageable) {
        return bookingRepository.findByCaseId_IdAndDeletedAtIsNull(caseId, pageable)
            .map(BookingDTO::new);
    }

    public Page<BookingDTO> searchBy(
        @Nullable UUID caseId,
        @Nullable String caseReference,
        @Nullable UUID courtId,
        Optional<Timestamp> scheduledFor,
        @Nullable UUID participantId,
        Pageable pageable
    ) {
        var until = scheduledFor.isEmpty()
            ? null
            : scheduledFor.map(
                t -> Timestamp.from(t.toInstant().plus(86399, ChronoUnit.SECONDS))).orElse(null);

        var auth = ((UserAuthentication) SecurityContextHolder.getContext().getAuthentication());
        var authorisedBookings = auth.isAdmin() || auth.isAppUser() ? null : auth.getSharedBookings();
        var authorisedCourt = auth.isPortalUser() ? null : auth.getCourtId();

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

        var isUpdate = bookingRepository.existsById(createBookingDTO.getId());

        var caseEntity = caseRepository.findByIdAndDeletedAtIsNull(createBookingDTO.getCaseId())
            .orElse(null);

        if ((!isUpdate && caseEntity == null)
            || (isUpdate && createBookingDTO.getCaseId() != null && caseEntity == null)
        ) {
            throw new NotFoundException("Case: " + createBookingDTO.getCaseId());
        }

        var bookingEntity = bookingRepository.findById(createBookingDTO.getId()).orElse(new Booking());

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

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    private boolean bookingAlreadyDeleted(UUID id) {
        return bookingRepository.existsByIdAndDeletedAtIsNotNull(id);
    }

    @Transactional
    @PreAuthorize("@authorisationService.hasBookingAccess(authentication, #id)")
    public void markAsDeleted(UUID id) {
        var entity = bookingRepository.findByIdAndDeletedAtIsNull(id);
        if (entity.isEmpty()) {
            throw new NotFoundException("Booking: " + id);
        }
        var booking = entity.get();
        captureSessionService.deleteCascade(booking);
        shareBookingService.deleteCascade(booking);
        bookingRepository.deleteById(id);
    }

    @Transactional
    public void deleteCascade(Case caseEntity) {
        bookingRepository
            .findAllByCaseIdAndDeletedAtIsNull(caseEntity)
            .forEach((booking) -> {
                captureSessionService.deleteCascade(booking);
                shareBookingService.deleteCascade(booking);
            });
        bookingRepository.deleteAllByCaseId(caseEntity);
    }
}
