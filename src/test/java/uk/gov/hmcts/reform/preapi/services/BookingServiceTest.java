package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookingService.class)
class BookingServiceTest {

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private CaseRepository caseRepository;

    @MockBean
    private RecordingRepository recordingRepository;

    @MockBean
    private ParticipantRepository participantRepository;

    @Autowired
    private BookingService bookingService;

    @DisplayName("Find All By CaseId")
    @Test
    void findAllByCaseIdSuccess() {


        var courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(courtEntity);
        var bookingEntity1 = new Booking();
        bookingEntity1.setId(UUID.randomUUID());
        bookingEntity1.setCaseId(caseEntity);
        var bookingEntity2 = new Booking();
        bookingEntity2.setId(UUID.randomUUID());
        bookingEntity2.setCaseId(caseEntity);
        var bookingModel1 = new BookingDTO(bookingEntity1);
        var bookingModel2 = new BookingDTO(bookingEntity2);

        when(bookingRepository.findByCaseId_IdAndDeletedAtIsNull(caseEntity.getId(), null))
            .thenReturn(new PageImpl<>(new ArrayList<>() {
                {
                    add(bookingEntity1);
                    add(bookingEntity2);
                }
            }));
        assertThat(bookingService.findAllByCaseId(caseEntity.getId(), null).getContent())
            .isEqualTo(new ArrayList<>() {
                    {
                    add(bookingModel1);
                    add(bookingModel2);
                }
            });

    }

    @DisplayName("Search By Case Ref")
    @Test
    void searchByCaseRefSuccess() {


        var courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        var caseEntity1 = new Case();
        caseEntity1.setId(UUID.randomUUID());
        caseEntity1.setCourt(courtEntity);
        caseEntity1.setReference("MyRef1");
        var caseEntity2 = new Case();
        caseEntity2.setId(UUID.randomUUID());
        caseEntity2.setCourt(courtEntity);
        caseEntity2.setReference("MyRef2");
        var bookingEntity1 = new Booking();
        bookingEntity1.setId(UUID.randomUUID());
        bookingEntity1.setCaseId(caseEntity1);
        var bookingEntity2 = new Booking();
        bookingEntity2.setId(UUID.randomUUID());
        bookingEntity2.setCaseId(caseEntity2);
        var bookingModel1 = new BookingDTO(bookingEntity1);
        var bookingModel2 = new BookingDTO(bookingEntity2);

        when(bookingRepository.searchBookingsBy(null, "MyRef", null))
            .thenReturn(new PageImpl<>(new ArrayList<>() {
                {
                    add(bookingEntity1);
                    add(bookingEntity2);
                }
            }));
        assertThat(bookingService.searchBy(null, "MyRef", null).getContent()).isEqualTo(new ArrayList<>() {
            {
                add(bookingModel1);
                add(bookingModel2);
            }
        });
    }

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
        when(recordingRepository.searchAllBy(null, null, null))
            .thenReturn(new PageImpl<>(Collections.emptyList()));
        assertThat(bookingService.findById(bookingId)).isEqualTo(bookingModel);
    }

    @DisplayName("Create a booking")
    @Test
    void upsertBookingSuccessCreated() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        var participantModel = new CreateParticipantDTO();
        participantModel.setId(UUID.randomUUID());
        participantModel.setParticipantType(ParticipantType.WITNESS);
        participantModel.setFirstName("John");
        participantModel.setLastName("Smith");
        bookingModel.setParticipants(Set.of(participantModel));

        var bookingEntity = new Booking();
        var participantEntity = new Participant();

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        when(participantRepository.findById(participantModel.getId())).thenReturn(Optional.empty());
        when(participantRepository.save(any())).thenReturn(participantEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Create a booking when case not found")
    @Test
    void upsertCreateBookingCaseNotFound() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> bookingService.upsert(bookingModel)
        );

        verify(bookingRepository, times(1)).existsById(bookingModel.getId());
        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(bookingModel.getId());
        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingRepository, never()).findById(bookingModel.getId());
        verify(bookingRepository, never()).save(any());
    }

    @DisplayName("Update a booking when case not found")
    @Test
    void upsertUpdateBookingCaseNotFound() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();
        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);

        assertThrows(
            NotFoundException.class,
            () -> bookingService.upsert(bookingModel)
        );

        verify(bookingRepository, times(1)).existsById(bookingModel.getId());
        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(bookingModel.getId());
        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingRepository, never()).findById(bookingModel.getId());
        verify(bookingRepository, never()).save(any());
    }

    @DisplayName("Update a booking")
    @Test
    void upsertBookingSuccessUpdated() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();
        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Update a booking when case doesn't exist")
    @Test
    void upsertBookingFailureCaseDoesntExist() {

        var bookingModel = new CreateBookingDTO();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(UUID.randomUUID());
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();

        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.empty());

        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Not found: Case: " + bookingModel.getCaseId());
    }

    @DisplayName("Update a booking when booking has been deleted")
    @Test
    void upsertBookingFailureAlreadyDeleted() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(true);
        assertThatExceptionOfType(ResourceInDeletedStateException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Resource BookingDTO("
                             + bookingModel.getId().toString()
                             + ") is in a deleted state and cannot be updated");
    }

    @DisplayName("Update a booking when participant has been deleted")
    @Test
    void upsertBookingFailureParticipantAlreadyDeleted() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());
        var participantModel = new CreateParticipantDTO();
        participantModel.setId(UUID.randomUUID());
        participantModel.setParticipantType(ParticipantType.WITNESS);
        participantModel.setFirstName("John");
        participantModel.setLastName("Smith");
        bookingModel.setParticipants(Set.of(participantModel));

        var participantEntity = new Participant();
        participantEntity.setId(participantModel.getId());
        participantEntity.setDeletedAt(Timestamp.from(Instant.now()));
        var bookingEntity = new Booking();

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);

        when(participantRepository.findById(participantModel.getId())).thenReturn(Optional.of(participantEntity));
        assertThatExceptionOfType(ResourceInDeletedStateException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Resource Participant("
                             + participantModel.getId().toString()
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

        bookingService.markAsDeleted(bookingId);
        verify(bookingRepository, times(1)).deleteById(bookingId);
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
        verify(bookingRepository, times(0)).deleteById(bookingId);
    }
}
