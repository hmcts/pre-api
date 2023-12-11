package uk.gov.hmcts.reform.preapi.services;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RecordingRepository recordingRepository;

    @Autowired
    public BookingService(final BookingRepository bookingRepository,
                          final RecordingRepository recordingRepository) {
        this.bookingRepository = bookingRepository;
        this.recordingRepository = recordingRepository;
    }

    public BookingDTO findById(UUID id) {

        return bookingRepository.findByIdAndDeletedAtIsNull(id)
            .map(BookingDTO::new)
            .orElseThrow(() -> new NotFoundException("BookingDTO not found"));
    }

    public UpsertResult upsert(CreateBookingDTO createBookingDTO) {
        if (bookingAlreadyDeleted(createBookingDTO.getId())) {
            throw new ResourceInDeletedStateException("BookingDTO", createBookingDTO.getId().toString());
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
            entity.get().setDeletedAt(new Timestamp(System.currentTimeMillis()));
            bookingRepository.save(entity.get());
            recordingRepository.searchAllBy(id, null, null)
                .forEach(recording -> {
                    recording.setDeletedAt(new Timestamp(System.currentTimeMillis()));
                    recordingRepository.save(recording);
                });
        }
    }
}
