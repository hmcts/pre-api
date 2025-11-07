package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.BadRequestException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.repositories.RecordingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = BookingService.class)
class BookingServiceTest {

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private CaseRepository caseRepository;

    @MockitoBean
    private RecordingRepository recordingRepository;

    @MockitoBean
    private ParticipantRepository participantRepository;

    @MockitoBean
    private CaptureSessionService captureSessionService;

    @MockitoBean
    private ShareBookingService shareBookingService;

    @MockitoBean
    private CaseService caseService;

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

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);

        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        when(bookingRepository.searchBookingsBy(
            null,
            "MyRef",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            null
        ))
            .thenReturn(new PageImpl<>(new ArrayList<>() {
                {
                    add(bookingEntity1);
                    add(bookingEntity2);
                }
            }));
        assertThat(
            bookingService
                .searchBy(null, "MyRef", null, Optional.empty(), null, null, null, null, null)
                .getContent()).isEqualTo(List.of(bookingModel1, bookingModel2));
    }

    @DisplayName("Get a booking")
    @Test
    @SuppressWarnings({"PMD.LinguisticNaming", "PMD.LawOfDemeter"})
    void getBookingSuccess() {

        var bookingId = UUID.randomUUID();
        var bookingEntity = new Booking();
        bookingEntity.setId(bookingId);
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        var courtEntity = new Court();
        caseEntity.setCourt(courtEntity);
        bookingEntity.setCaseId(caseEntity);
        var bookingModel = new BookingDTO(bookingEntity);

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(Optional.of(bookingEntity));
        when(recordingRepository.searchAllBy(null, false, false,null))
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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));
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
        when(participantRepository.existsByIdAndCaseId_Id(participantModel.getId(), caseId)).thenReturn(true);
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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> bookingService.upsert(bookingModel)
        );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(bookingModel.getId());
        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingRepository, times(1)).findById(bookingModel.getId());
        verify(bookingRepository, never()).save(any());
    }

    @DisplayName("Create/update a booking when case is not OPEN")
    @Test
    void upsertCreateBookingCaseNotOpen() {
        setAuthentication();

        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setState(CaseState.CLOSED);

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(aCase));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> bookingService.upsert(bookingModel)
        ).getMessage();
        assertThat(message).isEqualTo(
            "Resource Booking(" + bookingModel.getId()
                + ") is associated with a case in the state CLOSED. Must be in state OPEN."
        );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(bookingModel.getId());
        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingRepository, times(1)).findById(bookingModel.getId());
        verify(bookingRepository, never()).save(any());
    }

    @DisplayName("Create a booking in the past as a superuser")
    @Test
    void upsertBookingInPastSuperuserCreated() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.hasRole("ROLE_SUPER_USER")).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().minus(Period.ofWeeks(1))));
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);
        assertThat(bookingService.upsert(bookingModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Create a booking in the past as a standard user")
    @Test
    void upsertBookingInPastWithoutSuperuserCreated() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.hasRole("ROLE_SUPER_USER")).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        var caseEntity = new Case();
        caseEntity.setId(caseId);
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().minus(Period.ofWeeks(1))));
        bookingModel.setParticipants(Set.of());

        var bookingEntity = new Booking();

        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.empty());

        assertThatExceptionOfType(BadRequestException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Scheduled date must not be in the past");
    }


    @DisplayName("Update a booking when case not found")
    @Test
    void upsertUpdateBookingCaseNotFound() {
        var bookingModel = new CreateBookingDTO();
        var caseId = UUID.randomUUID();
        bookingModel.setId(UUID.randomUUID());
        bookingModel.setCaseId(caseId);
        bookingModel.setParticipants(Set.of());
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

        var bookingEntity = new Booking();
        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.empty());
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(true);

        assertThrows(
            NotFoundException.class,
            () -> bookingService.upsert(bookingModel)
        );

        verify(bookingRepository, times(1)).existsByIdAndDeletedAtIsNotNull(bookingModel.getId());
        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingRepository, times(1)).findById(bookingModel.getId());
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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));
        var participantModel = new CreateParticipantDTO();
        participantModel.setId(UUID.randomUUID());
        participantModel.setParticipantType(ParticipantType.WITNESS);
        participantModel.setFirstName("John");
        participantModel.setLastName("Smith");

        bookingModel.setParticipants(Set.of(participantModel));

        var participantEntity = new Participant();
        participantEntity.setId(participantModel.getId());
        participantEntity.setDeletedAt(Timestamp.from(Instant.now()));
        caseEntity.setParticipants(Set.of(participantEntity));
        var bookingEntity = new Booking();

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.of(bookingEntity));
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(bookingRepository.save(bookingEntity)).thenReturn(bookingEntity);
        when(participantRepository.existsByIdAndCaseId_Id(participantModel.getId(), caseId)).thenReturn(true);

        when(participantRepository.findById(participantModel.getId())).thenReturn(Optional.of(participantEntity));
        assertThatExceptionOfType(ResourceInDeletedStateException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Resource Participant("
                             + participantModel.getId().toString()
                             + ") is in a deleted state and cannot be updated");
    }

    @DisplayName("Create a booking with a participant not assigned to the case")
    @Test
    void createBookingWithParticipantNotAssignedToTheCase() {
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
        bookingModel.setScheduledFor(Timestamp.from(Instant.now().plus(Period.ofWeeks(1))));

        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findById(bookingModel.getId())).thenReturn(Optional.empty());
        when(bookingRepository.existsByIdAndDeletedAtIsNotNull(bookingModel.getId())).thenReturn(false);
        when(bookingRepository.existsById(bookingModel.getId())).thenReturn(false);
        when(caseRepository.findByIdAndDeletedAtIsNull(bookingModel.getCaseId())).thenReturn(Optional.of(new Case()));
        when(participantRepository.existsByIdAndCaseId_Id(participantModel.getId(), caseId)).thenReturn(false);

        assertThatExceptionOfType(NotFoundException.class)
            .isThrownBy(() -> {
                bookingService.upsert(bookingModel);
            })
            .withMessage("Not found: Participant: " + participantModel.getId() + " in case: " + caseId);
    }

    @DisplayName("Delete a booking")
    @Test
    void deleteBookingSuccess() {
        var bookingId = UUID.randomUUID();
        var bookingEntity = new Booking();
        bookingEntity.setId(bookingId);

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(Optional.of(bookingEntity));

        bookingService.markAsDeleted(bookingId);

        verify(shareBookingService, times(1)).deleteCascade(bookingEntity);
        verify(captureSessionService, times(1)).deleteCascade(bookingEntity);
        verify(bookingRepository, times(1)).saveAndFlush(bookingEntity);
    }

    @DisplayName("Delete a booking that doesn't exist")
    @Test
    void deleteBookingSuccessAlthoughDoesntExist() {
        var bookingId = UUID.randomUUID();

        when(bookingRepository.findByIdAndDeletedAtIsNull(bookingId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> bookingService.markAsDeleted(bookingId));

        verify(shareBookingService, never()).deleteCascade(any(Booking.class));
        verify(captureSessionService, never()).deleteCascade(any(Booking.class));
        verify(bookingRepository, never()).deleteById(bookingId);
    }

    @DisplayName("Should delete all attached capture sessions and shares before marking booking as deleted")
    @Test
    void deleteCascadeSuccess() {
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);

        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(caseEntity)).thenReturn(List.of(booking));

        bookingService.deleteCascade(caseEntity);

        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(caseEntity);
        verify(shareBookingService, times(1)).deleteCascade(booking);
        verify(captureSessionService, times(1)).deleteCascade(booking);
        verify(bookingRepository, times(1)).save(booking);
    }

    @DisplayName("Should clean any unused capture sessions without marking booking as deleted")
    @Test
    void cleanUnusedCaptureSessionsSuccess() {
        var captureSessionFailedRecording = new CaptureSession();
        captureSessionFailedRecording.setStatus(RecordingStatus.FAILURE);

        var captureSessionRecordingAvailable = new CaptureSession();
        captureSessionRecordingAvailable.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaptureSessions(Set.of(captureSessionFailedRecording, captureSessionRecordingAvailable));

        bookingService.cleanUnusedCaptureSessions(booking);

        verify(captureSessionService, times(1)).deleteById(captureSessionFailedRecording.getId());
        verifyNoMoreInteractions(captureSessionService);
        verifyNoMoreInteractions(bookingRepository);
    }

    @DisplayName("Should ignore any unused capture sessions that are already deleted")
    @Test
    void ignoreDeletedCaptureSessionsSuccess() {
        var captureSessionFailedRecording = new CaptureSession();
        captureSessionFailedRecording.setStatus(RecordingStatus.FAILURE);
        captureSessionFailedRecording.setDeletedAt(Timestamp.from(Instant.now()));

        var captureSessionRecordingAvailable = new CaptureSession();
        captureSessionRecordingAvailable.setStatus(RecordingStatus.RECORDING_AVAILABLE);

        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaptureSessions(Set.of(captureSessionFailedRecording, captureSessionRecordingAvailable));

        bookingService.cleanUnusedCaptureSessions(booking);

        verifyNoMoreInteractions(captureSessionService);
        verifyNoMoreInteractions(bookingRepository);
    }

    @DisplayName("Should undelete a booking successfully when booking is marked as deleted")
    @Test
    void undeleteSuccess() {
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setDeletedAt(Timestamp.from(Instant.now()));
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setDeletedAt(Timestamp.from(Instant.now()));
        booking.setCaseId(aCase);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        bookingService.undelete(booking.getId());

        verify(bookingRepository, times(1)).findById(booking.getId());
        verify(caseService, times(1)).undelete(aCase.getId());
        verify(bookingRepository, times(1)).save(booking);
    }

    @DisplayName("Should do nothing when booking is not deleted")
    @Test
    void undeleteNotDeletedSuccess() {
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(aCase);

        when(bookingRepository.findById(booking.getId())).thenReturn(Optional.of(booking));

        bookingService.undelete(booking.getId());

        verify(bookingRepository, times(1)).findById(booking.getId());
        verify(caseService, times(1)).undelete(aCase.getId());
        verify(bookingRepository, never()).save(booking);
    }

    @DisplayName("Should throw not found exception when booking cannot be found")
    @Test
    void undeleteNotFound() {
        var bookingId = UUID.randomUUID();

        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> bookingService.undelete(bookingId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Booking: " + bookingId);

        verify(bookingRepository, times(1)).findById(bookingId);
        verify(bookingRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find all past bookings without capture sessions")
    void findAllPastBookings() {
        var courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(courtEntity);
        var booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setCaseId(caseEntity);
        booking.setScheduledFor(Timestamp.from(Instant.now()));
        booking.setParticipants(Set.of());
        booking.setCaptureSessions(Set.of());
        booking.setShares(Set.of());
        booking.setCreatedAt(Timestamp.from(Instant.now()));
        booking.setModifiedAt(Timestamp.from(Instant.now()));
        when(bookingRepository.findAllPastUnusedBookings(any()))
            .thenReturn(List.of(booking));

        List<BookingDTO> bookings = bookingService.findAllPastBookings();

        assertThat(bookings).hasSize(1);
        assertThat(bookings.getFirst().getId()).isEqualTo(booking.getId());

        verify(bookingRepository, times(1))
            .findAllPastUnusedBookings(any());
    }

    @Test
    @DisplayName("Should find all bookings scheduled for today")
    void findAllBookingsForToday() {
        LocalDate currentDate = LocalDate.now();

        var court = createCourt();
        var booking1 = createBooking(court, Timestamp.valueOf(currentDate.atTime(LocalTime.of(10, 0))));
        var booking2 = createBooking(court, Timestamp.valueOf(currentDate.atTime(LocalTime.of(15, 0))));
        var bookingsForToday = List.of(booking1, booking2);
        setAuthentication();

        when(bookingRepository.searchBookingsBy(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            anyBoolean(),
            any())).thenReturn(new PageImpl<>(List.of(booking1, booking2)));

        var bookings = bookingService.findAllBookingsForToday();

        assertThat(bookings).hasSize(bookingsForToday.size());
        verify(bookingRepository, times(1))
            .searchBookingsBy(
                any(),
                any(),
                any(),
                eq(Timestamp.valueOf(currentDate.atStartOfDay())),
                eq(Timestamp.valueOf(currentDate.atTime(23, 59, 59))),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyBoolean(),
                any());
    }

    private Court createCourt() {
        var court = new Court();
        court.setId(UUID.randomUUID());
        return court;
    }

    private Booking createBooking(Court court, Timestamp scheduledFor) {
        var caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        caseEntity.setCourt(court);
        return HelperFactory.createBooking(caseEntity, scheduledFor, null);
    }

    private void setAuthentication() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        when(mockAuth.isAppUser()).thenReturn(true);
        when(mockAuth.hasRole("ROLE_SUPER_USER")).thenReturn(false);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);
    }
}

