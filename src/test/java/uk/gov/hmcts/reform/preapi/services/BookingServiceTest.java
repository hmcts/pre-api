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
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;

import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;


import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookingService.class)
class BookingServiceTest {

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private RecordingRepository recordingRepository;

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

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(java.util.Optional.of(bookingEntity));
        when(recordingRepository.searchAllBy(bookingId, null, null))
            .thenReturn(Collections.emptyList());
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

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
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

        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Update a booking deleted booking")
    @Test
    void upsertBookingFailureAlreadyDeleted() {

        var bookingModel = new CreateBookingDTO();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();

        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(true);
        assertThatExceptionOfType(ResourceInDeletedStateException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Resource BookingDTO("
                             + bookingModel.getId().toString()
                             + ") is in a deleted state and cannot be updated");
    }

    @DisplayName("Delete a booking")
    @Test
    void deleteBookingSuccess() {

        var bookingId = UUID.randomUUID();
        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();
        bookingEntity.setId(bookingId);

        var recordingEntity = new uk.gov.hmcts.reform.preapi.entities.Recording();
        recordingEntity.setId(UUID.randomUUID());

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(java.util.Optional.of(bookingEntity));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);
        when(recordingRepository.searchAllBy(bookingId, null, null))
            .thenReturn(new ArrayList<Recording>() {
                {
                    add(recordingEntity);
                }
            });

        bookingService.markAsDeleted(bookingId);
        assertThat(bookingEntity.getDeletedAt()).isNotNull();
        assertTrue(recordingEntity.isDeleted());
        verify(bookingRepository, times(1)).save(bookingEntity);
        verify(recordingRepository, times(1)).save(recordingEntity);
    }

    @DisplayName("Delete a booking that doesn't exist")
    @Test
    void deleteBookingSuccessAlthoughDoesntExist() {

        var bookingId = UUID.randomUUID();
        var bookingEntity = new uk.gov.hmcts.reform.preapi.entities.Booking();
        bookingEntity.setId(bookingId);

        var recordingEntity = new uk.gov.hmcts.reform.preapi.entities.Recording();
        recordingEntity.setId(UUID.randomUUID());

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(java.util.Optional.empty());
        verify(bookingRepository, times(0)).save(bookingEntity);
        verify(recordingRepository, times(0)).save(recordingEntity);
    }
}
