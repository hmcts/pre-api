package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.model.Booking;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;

    @Autowired
    public BookingService(final BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    public UpsertResult upsert(Booking booking) {
        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();
        var caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(booking.getCaseId());
        bookingEntity.setId(booking.getId());
        bookingEntity.setCaseId(caseEntity);
        bookingEntity.setParticipants(booking.getParticipants());
        bookingEntity.setScheduledFor(booking.getScheduledFor());

        var updated = bookingRepository.existsById(booking.getId());
        bookingRepository.save(bookingEntity);

        return updated ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }
}
