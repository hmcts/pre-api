package uk.gov.hmcts.reform.preapi.services;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CaseService.class)
@SuppressWarnings("PMD.LawOfDemeter")
class CaseServiceTest {

    private static uk.gov.hmcts.reform.preapi.entities.Case caseEntity;

    private static List<Case> allCaseEntities = new ArrayList<>();

    @MockBean
    private CaseRepository caseRepository;

    @MockBean
    private CourtRepository courtRepository;

    @Autowired
    private CaseService caseService;

    @BeforeAll
    static void setUp() {
        caseEntity = new uk.gov.hmcts.reform.preapi.entities.Case();
        caseEntity.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        caseEntity.setCourt(court);
        caseEntity.setReference("1234567890");
        caseEntity.setTest(false);
        caseEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));

        allCaseEntities.add(caseEntity);
    }

    @BeforeEach
    void resetDelete() {
        caseEntity.setDeletedAt(null);
    }

    @DisplayName("Find a case by it's id and return a model")
    @Test
    void findCaseByIdSuccess() {
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.ofNullable(caseEntity));

        var model = caseService.findById(caseEntity.getId());
        assertThat(model.getId()).isEqualTo(caseEntity.getId());
        assertThat(model.getCourtId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find a case by it's id which is missing")
    @Test
    void findCaseByIdMissing() {
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.ofNullable(null));

        var model = caseService.findById(caseEntity.getId());
        assertThat(model).isNull();
    }

    @DisplayName("Find all cases and return a list of models")
    @Test
    void findAllSuccess() {
        when(caseRepository.searchCasesBy(null, null)).thenReturn(allCaseEntities);

        List<uk.gov.hmcts.reform.preapi.model.Case> models = caseService.searchBy(null, null);
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.get(0).getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get(0).getCourtId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where reference is in list")
    @Test
    void findAllReferenceParamSuccess() {
        when(caseRepository.searchCasesBy("234", null)).thenReturn(allCaseEntities);

        List<uk.gov.hmcts.reform.preapi.model.Case> models = caseService.searchBy("234", null);
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.get(0).getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get(0).getCourtId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where reference is not the in list")
    @Test
    void findAllReferenceParamNotFoundSuccess() {
        when(caseRepository.searchCasesBy("abc", null)).thenReturn(Collections.emptyList());

        List<uk.gov.hmcts.reform.preapi.model.Case> models = caseService.searchBy("234", null);
        assertThat(models.size()).isEqualTo(0);
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamSuccess() {
        when(caseRepository.searchCasesBy(null, caseEntity.getCourt().getId())).thenReturn(allCaseEntities);

        List<uk.gov.hmcts.reform.preapi.model.Case> models = caseService.searchBy(null, caseEntity.getCourt().getId());
        assertThat(models.size()).isEqualTo(1);
        assertThat(models.get(0).getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get(0).getCourtId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamNotFoundSuccess() {
        UUID uuid = UUID.randomUUID();
        when(caseRepository.searchCasesBy(null, uuid)).thenReturn(Collections.emptyList());

        List<uk.gov.hmcts.reform.preapi.model.Case> models = caseService.searchBy(null, uuid);
        assertThat(models.size()).isEqualTo(0);
    }

    @Test
    void createSuccess() {
        Case testingCase = createTestingCase();
        uk.gov.hmcts.reform.preapi.model.Case caseModel = new uk.gov.hmcts.reform.preapi.model.Case(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.ofNullable(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.empty());

        caseService.create(caseModel);

        verify(courtRepository, times(1)).findById(caseModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseModel.getId());
        verify(caseRepository, times(1)).save(any());
    }

    @Test
    void createCourtNotFound() {
        Case testingCase = createTestingCase();
        uk.gov.hmcts.reform.preapi.model.Case caseModel = new uk.gov.hmcts.reform.preapi.model.Case(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.create(caseModel));

        // Verify that repository methods are not called
        verify(courtRepository, times(1)).findById(caseModel.getCourtId());
        verify(caseRepository, never()).findById(any());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void createDuplicateCaseId() {
        Case testingCase = createTestingCase();
        uk.gov.hmcts.reform.preapi.model.Case caseModel = new uk.gov.hmcts.reform.preapi.model.Case(testingCase);
        when(courtRepository.findById(caseModel.getCourtId())).thenReturn(Optional.ofNullable(testingCase.getCourt()));
        when(caseRepository.findById(caseModel.getId()))
            .thenReturn(Optional.of(testingCase));

        assertThrows(ConflictException.class, () -> caseService.create(caseModel));

        verify(courtRepository, times(1)).findById(caseModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseModel.getId());
        verify(caseRepository, never()).save(any());
    }

    @Test
    void createDataIntegrityViolationException() {
        Case testingCase = createTestingCase();
        uk.gov.hmcts.reform.preapi.model.Case caseModel = new uk.gov.hmcts.reform.preapi.model.Case(testingCase);

        when(courtRepository.findById(caseModel.getCourtId())).thenReturn(Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(caseModel.getId())).thenReturn(Optional.empty());

        doThrow(DataIntegrityViolationException.class).when(caseRepository).save(any());

        assertThrows(DataIntegrityViolationException.class, () -> caseService.create(caseModel));

        verify(courtRepository, times(1)).findById(caseModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseModel.getId());
        verify(caseRepository, times(1)).save(any());
    }

    Case createTestingCase() {
        var testCase = new uk.gov.hmcts.reform.preapi.entities.Case();
        testCase.setId(UUID.randomUUID());
        var court = new Court();
        court.setId(UUID.randomUUID());
        testCase.setCourt(court);
        testCase.setReference("0987654321");
        testCase.setTest(false);
        testCase.setDeletedAt(null);
        testCase.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        testCase.setModifiedAt(Timestamp.from(java.time.Instant.now()));
        return testCase;
    }


    @Test
    void deleteByIdSuccess() {
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));

        caseService.deleteById(caseEntity.getId());

        verify(caseRepository, times(1)).findById(caseEntity.getId());
        verify(caseRepository, times(1)).save(caseEntity);

        assertThat(caseEntity).isNotNull();
    }

    @Test
    void deleteByIdNotFound() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.findById(caseId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> caseService.deleteById(caseId));

        verify(caseRepository, times(1)).findById(caseId);
        verify(caseRepository, never()).save(any());
    }

    @Test
    void deleteByIdAlreadyDeleted() {
        caseEntity.setDeletedAt(Timestamp.from(Instant.now()));
        when(caseRepository.findById(caseEntity.getId())).thenReturn(Optional.of(caseEntity));

        assertThrows(NotFoundException.class, () -> caseService.deleteById(caseEntity.getId()));

        verify(caseRepository, times(1)).findById(caseEntity.getId());
        verify(caseRepository, never()).save(any());
    }
}
