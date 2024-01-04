package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SuppressWarnings("PMD.SingularField")
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ParticipantRepository participantRepository;

    @Autowired
    public BookingService(final BookingRepository bookingRepository,
                          final ParticipantRepository participantRepository) {
        this.bookingRepository = bookingRepository;
        this.participantRepository = participantRepository;
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

    public List<BookingDTO> searchBy(String caseReference) {
        return bookingRepository
            .searchBookingsBy(caseReference)
            .stream()
            .map(BookingDTO::new)
            .collect(Collectors.toList());
    }

    public UpsertResult upsert(CreateBookingDTO createBookingDTO) {
        if (bookingAlreadyDeleted(createBookingDTO.getId())) {
            throw new ResourceInDeletedStateException("BookingDTO", createBookingDTO.getId().toString());
        }

        var bookingEntity = bookingRepository.findById(createBookingDTO.getId()).orElse(new Booking());
        var caseEntity = new Case();
        caseEntity.setId(createBookingDTO.getCaseId());
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

        var updated = bookingRepository.existsById(createBookingDTO.getId());
        bookingRepository.save(bookingEntity);

        return updated ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    private boolean bookingAlreadyDeleted(UUID id) {
        return bookingRepository.existsByIdAndDeletedAtIsNotNull(id);
    }

    @Transactional
    public void markAsDeleted(UUID id) {
        var entity = bookingRepository.findByIdAndDeletedAtIsNull(id);
        if (entity.isPresent()) {
            bookingRepository.deleteById(id);
        }
    }
}
