package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SuppressWarnings("PMD.SingularField")
public class BookingService {


    private final BookingRepository bookingRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ShareBookingRepository shareBookingRepository;
    private final CaseRepository caseRepository;

    private final CaptureSessionService captureSessionService;

    @Autowired
    public BookingService(final BookingRepository bookingRepository,
                          final CaseRepository caseRepository,
                          final ParticipantRepository participantRepository,
                          final UserRepository userRepository,
                          final ShareBookingRepository shareBookingRepository,
                          CaptureSessionService captureSessionService) {
        this.bookingRepository = bookingRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.shareBookingRepository = shareBookingRepository;
        this.caseRepository = caseRepository;
        this.captureSessionService = captureSessionService;
    }

    public BookingDTO findById(UUID id) {

        return bookingRepository.findByIdAndDeletedAtIsNull(id)
            .map(BookingDTO::new)
            .orElseThrow(() -> new NotFoundException("BookingDTO not found"));
    }

    public Page<BookingDTO> findAllByCaseId(UUID caseId, Pageable pageable) {
        return bookingRepository.findByCaseId_IdAndDeletedAtIsNull(caseId, pageable)
            .map(BookingDTO::new);
    }

    public Page<BookingDTO> searchBy(UUID caseId, String caseReference, Pageable pageable) {
        return bookingRepository
            .searchBookingsBy(caseId, caseReference, pageable)
            .map(BookingDTO::new);
    }

    @SuppressWarnings("PMD.CyclomaticComplexity")
    @Transactional
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
    public void markAsDeleted(UUID id) {
        var entity = bookingRepository.findByIdAndDeletedAtIsNull(id);
        if (entity.isPresent()) {
            captureSessionService.deleteCascade(entity.get());
            bookingRepository.deleteById(id);
        }
    }

    @Transactional
    public UpsertResult shareBookingById(ShareBookingDTO shareBookingDTO) {
        if (shareBookingRepository.existsById(shareBookingDTO.getId())) {
            throw new ConflictException("Share booking already exists");
        }

        final var booking = bookingRepository.findById(shareBookingDTO.getBookingId())
            .orElseThrow(() -> new NotFoundException("Booking: " + shareBookingDTO.getBookingId()));
        final var sharedByUser = userRepository.findById(shareBookingDTO.getSharedByUserId())
            .orElseThrow(() -> new NotFoundException("Shared by User: " + shareBookingDTO.getSharedByUserId()));
        final var sharedWithUser = userRepository.findById(shareBookingDTO.getSharedWithUserId())
            .orElseThrow(() -> new NotFoundException("Shared with User: " + shareBookingDTO.getSharedWithUserId()));

        var shareBookingEntity = new ShareBooking();
        shareBookingEntity.setId(shareBookingDTO.getId());
        shareBookingEntity.setBooking(booking);
        shareBookingEntity.setSharedBy(sharedByUser);
        shareBookingEntity.setSharedWith(sharedWithUser);
        shareBookingRepository.save(shareBookingEntity);

        return UpsertResult.CREATED;
    }

    @Transactional
    public void deleteCascade(Case caseEntity) {
        System.out.println("Booking called");
        bookingRepository
            .findAllByCaseIdAndDeletedAtIsNull(caseEntity)
            .forEach(captureSessionService::deleteCascade);
        bookingRepository.deleteAllByCaseId(caseEntity);
    }
}
