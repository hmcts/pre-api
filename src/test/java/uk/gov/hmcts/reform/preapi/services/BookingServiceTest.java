package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.model.Booking;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        bookingEntity.setCaseId(caseEntity);
        var bookingModel = new Booking(bookingEntity);

        when(bookingRepository.findById(bookingId)).thenReturn(java.util.Optional.of(bookingEntity));

        assertThat(bookingService.findById(bookingId)).isEqualTo(bookingModel);
    }

    @DisplayName("Create a booking")
    @Test
    void upsertBookingSuccessCreated() {

        var bookingModel = new Booking();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();

        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Update a booking")
    @Test
    void upsertBookingSuccessUpdated() {

        var bookingModel = new Booking();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();

        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.UPDATED);
    }
}
