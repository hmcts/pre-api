package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.UpdateDeletedException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    @Autowired
    public BookingService(final BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public BookingDTO findById(UUID id) {

        return bookingRepository.findById(id)
            .map(BookingDTO::new)
            .orElseThrow(() -> new NotFoundException("BookingDTO not found"));
    }

    public UpsertResult upsert(CreateBookingDTO createBookingDTO) {
        var foundBooking = bookingRepository.findById(createBookingDTO.getId());
        if (foundBooking.isPresent() && foundBooking.get().isDeleted()) {
            throw new UpdateDeletedException("Booking: " + createBookingDTO.getId());
        }
        var bookingEntity = new Booking();
        var caseEntity = new Case();

        caseEntity.setId(createBookingDTO.getCaseId());
        bookingEntity.setId(createBookingDTO.getId());
        bookingEntity.setCaseId(caseEntity);
        bookingEntity.setParticipants(
            Stream.ofNullable(createBookingDTO.getParticipants())
                .flatMap(participants -> participants.stream().map(model -> {
                    var entity = new uk.gov.hmcts.reform.preapi.entities.Participant();
                    entity.setId(model.getId());
                    entity.setFirstName(model.getFirstName());
                    entity.setLastName(model.getLastName());
                    entity.setParticipantType(model.getParticipantType());
                    return entity;
                }))
                .collect(Collectors.toSet()));
        bookingEntity.setScheduledFor(createBookingDTO.getScheduledFor());

        var updated = foundBooking.isPresent();
        bookingRepository.save(bookingEntity);

        return updated ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
