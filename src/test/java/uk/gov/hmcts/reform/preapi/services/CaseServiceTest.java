package uk.gov.hmcts.reform.preapi.services;

import feign.FeignException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.email.CaseStateChangeNotifierFlowClient;
import uk.gov.hmcts.reform.preapi.email.EmailServiceFactory;
import uk.gov.hmcts.reform.preapi.email.govnotify.GovNotify;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInWrongStateException;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.service.notify.NotificationClient;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CaseService.class)
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.TooManyMethods"})
class CaseServiceTest {

    private static Case caseEntity;

    private static List<Case> allCaseEntities = new ArrayList<>();

    @MockBean
    private CaseRepository caseRepository;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private ParticipantRepository participantRepository;

    @MockBean
    private BookingService bookingService;

    @MockBean
    private ShareBookingService shareBookingService;

    @MockBean
    private CaseStateChangeNotifierFlowClient caseStateChangeNotifierFlowClient;

    @MockBean
    private BookingRepository bookingRepository;

    @MockBean
    private EmailServiceFactory emailServiceFactory;

    @MockBean
    private NotificationClient notificationClient;

    @MockBean
    private GovNotify govNotify;

    @Autowired
    private CaseService caseService;

    @BeforeAll
    static void setUp() {
        caseEntity = new Case();
        caseEntity.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        caseEntity.setCourt(court);
        caseEntity.setReference("1234567890");
        caseEntity.setTest(false);
        caseEntity.setCreatedAt(Timestamp.from(Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(Instant.now()));

        allCaseEntities.add(caseEntity);
    }

    @BeforeEach
    void reset() {
        when(emailServiceFactory.getEnabledEmailService()).thenReturn(govNotify);
        when(emailServiceFactory.getEnabledEmailService(eq("GovNotify"))).thenReturn(govNotify);
        when(emailServiceFactory.isEnabled()).thenReturn(false);

        caseEntity.setDeletedAt(null);
        caseEntity.setState(CaseState.OPEN);
    }

    @DisplayName("Find a case by it's id and return a model")
    @Test
    void findCaseByIdSuccess() {
        when(caseRepository.findByIdAndDeletedAtIsNull(caseEntity.getId())).thenReturn(Optional.ofNullable(caseEntity));

        var model = caseService.findById(caseEntity.getId());
        assertThat(model.getId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find a case by it's id which does not exist")
    @Test
    void findCaseByIdNotFound() {
        var randomId = UUID.randomUUID();
        when(caseRepository.findByIdAndDeletedAtIsNull(randomId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.findById(randomId));
    }

    @DisplayName("Find a case by it's id which does not exist since it has been deleted")
    @Test
    void findCaseByIdNotFoundDeleted() {
        caseEntity.setDeletedAt(Timestamp.from(Instant.now()));
        when(caseRepository.findByIdAndDeletedAtIsNull(caseEntity.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.findById(caseEntity.getId()));
    }

    @DisplayName("Find all cases and return a list of models")
    @Test
    void findAllSuccess() {
        when(caseRepository.searchCasesBy(null, null, false, null,null)).thenReturn(new PageImpl<>(allCaseEntities));

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);


        Page<CaseDTO> models = caseService.searchBy(null, null, false, null);
        assertThat(models.getTotalElements()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return a list of models as non admin")
    @Test
    void findAllSuccessNonAdmin() {
        var courtId = UUID.randomUUID();
        when(caseRepository.searchCasesBy(null, null, false, courtId,null)).thenReturn(new PageImpl<>(allCaseEntities));

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isPortalUser()).thenReturn(false);
        when(mockAuth.isAdmin()).thenReturn(false);
        when(mockAuth.getCourtId()).thenReturn(courtId);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);


        var models = caseService.searchBy(null, null, false, null);
        assertThat(models.getTotalElements()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());

        verify(caseRepository, times(1)).searchCasesBy(null, null, false, courtId, null);
    }

    @DisplayName("Find all cases and return list of models where reference is in list")
    @Test
    void findAllReferenceParamSuccess() {
        when(caseRepository.searchCasesBy("234", null, false, null, null)).thenReturn(new PageImpl<>(allCaseEntities));

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        Page<CaseDTO> models = caseService.searchBy("234", null, false,null);
        assertThat(models.getTotalElements()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where reference is not the in list")
    @Test
    void findAllReferenceParamNotFoundSuccess() {
        when(caseRepository.searchCasesBy("abc", null, false, null,null)).thenReturn(Page.empty());

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        var models = caseService.searchBy("abc", null, false, null);
        assertThat(models.getTotalElements()).isEqualTo(0);
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamSuccess() {
        when(caseRepository.searchCasesBy(null, caseEntity.getCourt().getId(), false, null, null))
            .thenReturn(new PageImpl<>(allCaseEntities));

        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        Page<CaseDTO> models = caseService.searchBy(null, caseEntity.getCourt().getId(), false, null);
        assertThat(models.getTotalElements()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamNotFoundSuccess() {
        var mockAuth = mock(UserAuthentication.class);
        when(mockAuth.isAdmin()).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(mockAuth);

        UUID uuid = UUID.randomUUID();
        when(caseRepository.searchCasesBy(null, uuid, false, null,null)).thenReturn(Page.empty());

        Page<CaseDTO> models = caseService.searchBy(null, uuid, false,null);
        assertThat(models.getTotalElements()).isEqualTo(0);
    }

    @Test
    void createSuccess() {

        var participant1 = new CreateParticipantDTO();
        participant1.setId(UUID.randomUUID());
        participant1.setParticipantType(ParticipantType.WITNESS);
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        var participant2 = new CreateParticipantDTO();
        participant2.setId(UUID.randomUUID());
        participant2.setParticipantType(ParticipantType.DEFENDANT);
        participant2.setFirstName("Jane");
        participant2.setLastName("Doe");

        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);
        caseDTOModel.setParticipants(Set.of(participant1, participant2));

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.empty());
        when(participantRepository.findById(any())).thenReturn(Optional.empty());

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(participantRepository, times(2)).save(any(Participant.class));
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).saveAndFlush(any(Case.class));
    }

    @Test
    void updateSuccess() {
        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.of(caseEntity));

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should update case when attempting to change the case's state")
    void updateCaseChangeStateSuccess() {
        caseEntity.setState(CaseState.CLOSED);
        var testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);
        caseDTOModel.setState(CaseState.OPEN);

        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.of(caseEntity));

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.of(caseEntity));

        var result = caseService.upsert(caseDTOModel);
        assertThat(result).isEqualTo(UpsertResult.UPDATED);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should delete shares and send email notification when updating case to closed")
    void updateCaseClosedDeleteSharesSuccess() {
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.CLOSED);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.deleteCascade(any(Case.class))).thenReturn(Set.of(share));

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).deleteCascade(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should log when an error occurs attempting to send email notification when updating case to closed")
    void updateCaseClosedDeleteSharesEmailNotificationError() {
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.CLOSED);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.deleteCascade(any(Case.class))).thenReturn(Set.of(share));
        doThrow(FeignException.class).when(caseStateChangeNotifierFlowClient).emailAfterCaseStateChange(anyList());

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).deleteCascade(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should send email notifications to shares when updating case and cancelling closure")
    void updateCaseCancelClosureSuccess() {
        caseEntity.setState(CaseState.PENDING_CLOSURE);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.OPEN);
        caseDTOModel.setClosedAt(null);

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.getSharesForCase(any(Case.class))).thenReturn(Set.of(share));

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).getSharesForCase(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should log when an error occurs attempting to send email notification when cancelling closure")
    void updateCaseCancelClosureEmailNotificationError() {
        caseEntity.setState(CaseState.PENDING_CLOSURE);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.OPEN);
        caseDTOModel.setClosedAt(null);

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.getSharesForCase(any(Case.class))).thenReturn(Set.of(share));
        doThrow(FeignException.class).when(caseStateChangeNotifierFlowClient).emailAfterCaseStateChange(anyList());

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).getSharesForCase(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should send email notifications to shares when updating case to pending closure")
    void updateCasePendingClosureSuccess() {
        caseEntity.setState(CaseState.OPEN);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.PENDING_CLOSURE);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.getSharesForCase(any(Case.class))).thenReturn(Set.of(share));

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).getSharesForCase(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("Should log when occurs attempting to send email notification when updating case to pending closure")
    void updateCasePendingClosureEmailNotificationError() {
        caseEntity.setState(CaseState.OPEN);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.PENDING_CLOSURE);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.deleteCascade(any(Case.class))).thenReturn(Set.of(share));
        doThrow(FeignException.class).when(caseStateChangeNotifierFlowClient).emailAfterCaseStateChange(anyList());

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(shareBookingService, times(1)).getSharesForCase(any(Case.class));
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(anyList());
        verify(caseRepository, times(1)).saveAndFlush(any());
        verify(caseRepository, times(0)).save(any());
    }

    @Test
    void updateBadRequest() {
        caseEntity.setDeletedAt(Timestamp.from(Instant.now()));
        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.of(caseEntity));

        assertThrows(
            ResourceInDeletedStateException.class,
            () -> caseService.upsert(caseDTOModel)
        );

        verify(courtRepository, never()).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceInWrongStateException when attempting to update a case in wrong state")
    void updateCaseNotOpenBadRequest() {
        caseEntity.setState(CaseState.CLOSED);
        var testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);
        caseDTOModel.setState(CaseState.CLOSED);

        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.of(caseEntity));

        var message = assertThrows(
            ResourceInWrongStateException.class,
            () -> caseService.upsert(caseDTOModel)
        ).getMessage();

        assertThat(message).isEqualTo("Resource Case("
                                          + caseDTOModel.getId()
                                          + ") is in state CLOSED. Cannot update case unless in state OPEN.");

        verify(courtRepository, never()).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw ResourceInWrongStateException when setting state to PENDING_CLOSURE with open bookings")
    void updateCasePendingClosureBadRequest() {
        caseEntity.setState(CaseState.OPEN);
        var testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);
        caseDTOModel.setState(CaseState.PENDING_CLOSURE);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now().plusSeconds(86400))); // +1 day

        var booking = new Booking();
        booking.setCaptureSessions(Set.of());

        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.of(caseEntity));
        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(caseEntity))
            .thenReturn(List.of(booking));

        // has bookings that haven't been used yet
        var message1 = assertThrows(
            ResourceInWrongStateException.class,
            () -> caseService.upsert(caseDTOModel)
        ).getMessage();

        var expectedErrorMessage = "Resource Case("
            + caseDTOModel.getId()
            + ") has open bookings which must not be present when updating state to PENDING_CLOSURE";

        assertThat(message1).isEqualTo(expectedErrorMessage);

        // has bookings with capture sessions that are in not in an end state
        var captureSession = mock(CaptureSession.class);
        captureSession.setStatus(RecordingStatus.STANDBY);

        var message2 = assertThrows(
            ResourceInWrongStateException.class,
            () -> caseService.upsert(caseDTOModel)
        ).getMessage();
        assertThat(message2).isEqualTo(expectedErrorMessage);
    }

    @Test
    void createCaseReferenceFoundConflict() {
        var testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.empty());
        when(caseRepository.findAllByReferenceAndCourt_Id(caseDTOModel.getReference(), testingCase.getCourt().getId()))
            .thenReturn(List.of(testingCase));

        var message = assertThrows(
            ConflictException.class,
            () -> caseService.upsert(caseDTOModel)
        ).getMessage();

        assertThat(message).isEqualTo("Conflict: Case reference is already in use for this court");

        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1))
            .findAllByReferenceAndCourt_Id(caseDTOModel.getReference(), testingCase.getCourt().getId());
    }

    @Test
    void createCaseReferenceIsNullConflict() {
        var testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);
        caseDTOModel.setReference(null);

        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.empty());

        assertThrows(
            ConflictException.class,
            () -> caseService.upsert(caseDTOModel)
        );

        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1))
            .findAllByReferenceAndCourt_Id(caseDTOModel.getReference(), testingCase.getCourt().getId());
    }

    @Test
    void createCourtNotFound() {
        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.upsert(caseDTOModel));

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(any());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void createDataIntegrityViolationException() {
        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(courtRepository.findById(caseDTOModel.getCourtId())).thenReturn(Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(caseDTOModel.getId())).thenReturn(Optional.empty());

        doThrow(DataIntegrityViolationException.class).when(caseRepository).saveAndFlush(any());

        assertThrows(DataIntegrityViolationException.class, () -> caseService.upsert(caseDTOModel));

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).saveAndFlush(any());
    }

    @Test
    @DisplayName("Should successfully mark case as deleted and cascade")
    void deleteByIdSuccess() {
        when(caseRepository.findByIdAndDeletedAtIsNull(caseEntity.getId())).thenReturn(Optional.of(caseEntity));

        caseService.deleteById(caseEntity.getId());

        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseEntity.getId());
        verify(bookingService, times(1)).deleteCascade(caseEntity);
        verify(caseRepository, times(1)).saveAndFlush(caseEntity);
    }

    @Test
    @DisplayName("Should throw not found when case requested for deletion cannot be found")
    void deleteByIdNotFound() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findByIdAndDeletedAtIsNull(caseId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.deleteById(caseId));

        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseId);
        verify(bookingService, never()).deleteCascade(caseEntity);
        verify(caseRepository, never()).deleteById(caseId);
    }

    @DisplayName("Should undelete a case successfully when case is marked as deleted")
    @Test
    void undeleteSuccess() {
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());
        aCase.setDeletedAt(Timestamp.from(Instant.now()));

        when(caseRepository.findById(aCase.getId())).thenReturn(Optional.of(aCase));

        caseService.undelete(aCase.getId());

        verify(caseRepository, times(1)).findById(aCase.getId());
        verify(caseRepository, times(1)).save(aCase);
    }

    @DisplayName("Should do nothing when case is not deleted")
    @Test
    void undeleteNotDeletedSuccess() {
        var aCase = new Case();
        aCase.setId(UUID.randomUUID());

        when(caseRepository.findById(aCase.getId())).thenReturn(Optional.of(aCase));

        caseService.undelete(aCase.getId());

        verify(caseRepository, times(1)).findById(aCase.getId());
        verify(caseRepository, never()).save(aCase);
    }

    @DisplayName("Should throw not found exception when case cannot be found")
    @Test
    void undeleteNotFound() {
        var caseId = UUID.randomUUID();

        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        var message = assertThrows(
            NotFoundException.class,
            () -> caseService.undelete(caseId)
        ).getMessage();

        assertThat(message).isEqualTo("Not found: Case: " + caseId);

        verify(caseRepository, times(1)).findById(caseId);
        verify(caseRepository, never()).save(any());
    }

    @DisplayName("Should close pending cases that have closed_at value in the past")
    @Test
    void checkAndClosePendingCasesSuccess() {
        var instant = Instant.now().minusSeconds(1);
        var pendingCase = new Case();
        pendingCase.setState(CaseState.PENDING_CLOSURE);
        pendingCase.setClosedAt(Timestamp.from(instant.plus(28, ChronoUnit.DAYS)));

        var pendingCases = List.of(pendingCase);

        when(caseRepository.findAllByStateAndClosedAtBefore(eq(CaseState.PENDING_CLOSURE), any()))
            .thenReturn(pendingCases);

        caseService.closePendingCases();
        assertEquals(CaseState.CLOSED, pendingCase.getState());

        verify(caseRepository).findAllByStateAndClosedAtBefore(eq(CaseState.PENDING_CLOSURE), any());
        verify(caseRepository).save(pendingCase);
        verify(shareBookingService).deleteCascade(pendingCase);
        verify(caseStateChangeNotifierFlowClient, times(1)).emailAfterCaseStateChange(any());
        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(pendingCase);
    }

    @Test
    @DisplayName("Should delete bookings on closed if associated capture session's status is NO_RECORDING")
    void onCaseClosedDeleteBookingsNoRecording() {
        var bookingNoRecording = new Booking();
        bookingNoRecording.setId(UUID.randomUUID());
        var captureSessionNoRecording = new CaptureSession();
        captureSessionNoRecording.setStatus(RecordingStatus.NO_RECORDING);
        bookingNoRecording.setCaptureSessions(Set.of(captureSessionNoRecording));
        var aCase = new Case();

        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(aCase)).thenReturn(List.of(bookingNoRecording));

        caseService.onCaseClosed(aCase);

        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(aCase);
        verify(bookingService, times(1)).markAsDeleted(bookingNoRecording.getId());
    }

    @Test
    @DisplayName("Should delete bookings on closed if associated capture session's status is FAILURE")
    void onCaseClosedDeleteBookingsFailure() {
        var bookingFailure = new Booking();
        bookingFailure.setId(UUID.randomUUID());
        var captureSessionFailure = new CaptureSession();
        captureSessionFailure.setStatus(RecordingStatus.FAILURE);
        bookingFailure.setCaptureSessions(Set.of(captureSessionFailure));
        var aCase = new Case();

        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(aCase)).thenReturn(List.of(bookingFailure));

        caseService.onCaseClosed(aCase);

        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(aCase);
        verify(bookingService, times(1)).markAsDeleted(bookingFailure.getId());
    }

    @Test
    @DisplayName("Should not delete bookings on closed if associated capture session's status is RECORDING_AVAILABLE")
    void onCaseClosedDeleteBookingsRecordingAvailable() {
        var bookingRecordingAvailable = new Booking();
        bookingRecordingAvailable.setId(UUID.randomUUID());
        var captureSessionRecordingAvailable = new CaptureSession();
        captureSessionRecordingAvailable.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        bookingRecordingAvailable.setCaptureSessions(Set.of(captureSessionRecordingAvailable));
        var aCase = new Case();

        when(bookingRepository.findAllByCaseIdAndDeletedAtIsNull(aCase)).thenReturn(List.of(bookingRecordingAvailable));

        caseService.onCaseClosed(aCase);

        verify(bookingRepository, times(1)).findAllByCaseIdAndDeletedAtIsNull(aCase);
        verify(bookingService, never()).markAsDeleted(any());
    }

    @DisplayName("New email service should be used on case closed when enabled")
    @Test
    void caseClosedNewEmailServiceSuccess() {

        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.CLOSED);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.deleteCascade(any(Case.class))).thenReturn(Set.of(share));
        when(emailServiceFactory.isEnabled()).thenReturn(true);

        caseService.upsert(caseDTOModel);

        verify(govNotify, times(1)).caseClosed(any(), any());
        verify(caseStateChangeNotifierFlowClient, never()).emailAfterCaseStateChange(anyList());
    }

    @DisplayName("New email service should be used on case pending closure when enabled")
    @Test
    void casePendingClosureNewEmailServiceSuccess() {
        caseEntity.setState(CaseState.OPEN);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.PENDING_CLOSURE);
        caseDTOModel.setClosedAt(Timestamp.from(Instant.now()));

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.getSharesForCase(any(Case.class))).thenReturn(Set.of(share));
        when(emailServiceFactory.isEnabled()).thenReturn(true);

        caseService.upsert(caseDTOModel);

        verify(govNotify, times(1)).casePendingClosure(any(), any(), any());
        verify(caseStateChangeNotifierFlowClient, never()).emailAfterCaseStateChange(anyList());
    }

    @DisplayName("New email service should be used on case closure cancelled when enabled")
    @Test
    void caseClosureCancelledNewEmailServiceSuccess() {
        caseEntity.setState(CaseState.PENDING_CLOSURE);
        var caseDTOModel = new CreateCaseDTO(caseEntity);
        var share = createShare();
        share.setId(UUID.randomUUID());
        caseDTOModel.setState(CaseState.OPEN);
        caseDTOModel.setClosedAt(null);

        when(courtRepository.findById(caseEntity.getCourt().getId())).thenReturn(
            Optional.of(caseEntity.getCourt()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));
        when(shareBookingService.getSharesForCase(any(Case.class))).thenReturn(Set.of(share));
        when(emailServiceFactory.isEnabled()).thenReturn(true);

        caseService.upsert(caseDTOModel);

        verify(govNotify, times(1)).caseClosureCancelled(any(), any());
        verify(caseStateChangeNotifierFlowClient, never()).emailAfterCaseStateChange(anyList());
    }

    private Case createTestingCase() {
        var testCase = new Case();
        testCase.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        testCase.setCourt(court);
        testCase.setReference("0987654321");
        testCase.setTest(false);
        testCase.setDeletedAt(null);
        testCase.setCreatedAt(Timestamp.from(Instant.now()));
        testCase.setModifiedAt(Timestamp.from(Instant.now()));
        return testCase;
    }

    private ShareBooking createShare() {
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(user.getId().toString());
        user.setLastName(user.getId().toString());
        user.setEmail(user.getId() + "@example.com");

        var share = new ShareBooking();
        share.setId(UUID.randomUUID());
        share.setSharedWith(user);
        return share;
    }
}
