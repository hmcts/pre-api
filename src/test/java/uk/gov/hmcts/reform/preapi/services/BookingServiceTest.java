package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.UpdateDeletedException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookingService.class)
class BookingServiceTest {

    @MockBean
    private BookingRepository bookingRepository;

    @Autowired
    private BookingService bookingService;

    @DisplayName("Get a booking")
    @Test
    @SuppressWarnings({"PMD.LinguisticNaming", "PMD.LawOfDemeter"})
    void getBookingSuccess() {

        var bookingId = UUID.randomUUID();
        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();
        bookingEntity.setId(bookingId);
        var caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(UUID.randomUUID());
        var courtEntity = new uk.gov.hmcts.reform.preapi.entities.Court();
        caseEntity.setCourt(courtEntity);
        bookingEntity.setCaseId(caseEntity);
        var bookingModel = new BookingDTO(bookingEntity);

        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(bookingEntity));

        assertThat(bookingService.findById(bookingId)).isEqualTo(bookingModel);
    }

    @DisplayName("Create a booking")
    @Test
    void upsertBookingSuccessCreated() {

        var bookingModel = new CreateBookingDTO();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        var participantModel = new CreateParticipantDTO();
        participantModel.setId(UUID.randomUUID());
        participantModel.setParticipantType(ParticipantType.WITNESS);
        participantModel.setFirstName("John");
        participantModel.setLastName("Smith");
        bookingModel.setParticipants(Set.of(participantModel));

        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();

        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Update a booking")
    @Test
    void upsertBookingSuccessUpdated() {

        var bookingModel = new CreateBookingDTO();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();

        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity))
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Update a booking when booking has been deleted")
    @Test
    void upsertBookingBadRequestUpdate() {
        var bookingModel = new CreateBookingDTO();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();
        bookingEntity.setDeletedAt(Timestamp.from(Instant.now()));

        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity))
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThrows(
            UpdateDeletedException.class,
            () -> bookingService.upsert(bookingModel)
        );
    }
}
