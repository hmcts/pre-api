package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Booking;
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

    public Booking findById(UUID id) {

        return bookingRepository.findById(id)
            .map(Booking::new)
            .orElseThrow(() -> new NotFoundException("Booking not found"));
    }

    public UpsertResult upsert(Booking booking) {
        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();
        var caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(booking.getCaseId());
        bookingEntity.setId(booking.getId());
        bookingEntity.setCaseId(caseEntity);
        bookingEntity.setParticipants(
            Stream.ofNullable(booking.getParticipants())
                .flatMap(participants -> participants.stream().map(model -> {
                    var entity = new uk.gov.hmcts.reform.preapi.entities.Participant();
                    entity.setId(model.getId());
                    entity.setFirstName(model.getFirstName());
                    entity.setLastName(model.getLastName());
                    entity.setParticipantType(model.getParticipantType());
                    return entity;
                }))
                .collect(Collectors.toSet()));
        bookingEntity.setScheduledFor(booking.getScheduledFor());

        var updated = bookingRepository.existsById(booking.getId());
        bookingRepository.save(bookingEntity);

        return updated ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
