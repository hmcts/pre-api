package uk.gov.hmcts.reform.preapi.services;

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
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.ParticipantRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
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
    void resetDelete() {
        caseEntity.setDeletedAt(null);
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
        verify(caseRepository, times(1)).save(any(Case.class));
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
        verify(caseRepository, times(1)).save(any());
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

        doThrow(DataIntegrityViolationException.class).when(caseRepository).save(any());

        assertThrows(DataIntegrityViolationException.class, () -> caseService.upsert(caseDTOModel));

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).save(any());
    }

    Case createTestingCase() {
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


    @Test
    void deleteByIdSuccess() {
        when(caseRepository.findByIdAndDeletedAtIsNull(caseEntity.getId())).thenReturn(Optional.of(caseEntity));

        caseService.deleteById(caseEntity.getId());

        verify(caseRepository, times(1)).findByIdAndDeletedAtIsNull(caseEntity.getId());
        verify(bookingService, times(1)).deleteCascade(caseEntity);
        verify(caseRepository, times(1)).saveAndFlush(caseEntity);
    }

    @Test
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

//    @DisplayName("Should close pending cases that are older than 29 days")
//    @Test
//    void checkAndClosePendingCasesSuccess() {
//        Instant fixedInstant = Instant.parse("2024-07-09T12:00:00.000Z");
//        Timestamp thresholdTimestamp = Timestamp.from(fixedInstant.minusSeconds(29L * 24 * 60 * 60));
//        Timestamp closedAtTimestamp = Timestamp.from(fixedInstant.minusSeconds(30L * 24 * 60 * 60));
//
//        Case pendingCase = new Case();
//        pendingCase.setState(CaseState.PENDING_CLOSURE);
//        pendingCase.setClosedAt(closedAtTimestamp);
//
//        List<Case> pendingCases = List.of(pendingCase);
//
//        when(caseRepository.findByStateAndClosedAtBefore(CaseState.PENDING_CLOSURE, thresholdTimestamp))
//            .thenReturn(pendingCases);
//
//        caseService.closePendingCases();
//
//        verify(caseRepository).findByStateAndClosedAtBefore(CaseState.PENDING_CLOSURE, thresholdTimestamp);
//        verify(caseRepository).save(pendingCase);
//        assertEquals(CaseState.CLOSED, pendingCase.getState());
//    }

}
