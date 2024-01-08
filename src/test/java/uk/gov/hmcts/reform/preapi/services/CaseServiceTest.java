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
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
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
@SuppressWarnings({"PMD.LawOfDemeter", "PMD.TooManyMethods"})
class CaseServiceTest {

    private static Case caseEntity;

    private static List<Case> allCaseEntities = new ArrayList<>();

    @MockBean
    private CaseRepository caseRepository;

    @MockBean
    private CourtRepository courtRepository;

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
        when(caseRepository.searchCasesBy(null, null, null)).thenReturn(new PageImpl<>(allCaseEntities));

        Page<CaseDTO> models = caseService.searchBy(null, null, null);
        assertThat(models.getSize()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where reference is in list")
    @Test
    void findAllReferenceParamSuccess() {
        when(caseRepository.searchCasesBy("234", null, null)).thenReturn(new PageImpl<>(allCaseEntities));

        Page<CaseDTO> models = caseService.searchBy("234", null, null);
        assertThat(models.getSize()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where reference is not the in list")
    @Test
    void findAllReferenceParamNotFoundSuccess() {
        when(caseRepository.searchCasesBy("abc", null, null)).thenReturn(Page.empty());

        var models = caseService.searchBy("abc", null, null);
        assertThat(models.getSize()).isEqualTo(0);
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamSuccess() {
        when(caseRepository.searchCasesBy(null, caseEntity.getCourt().getId(), null)).thenReturn(new PageImpl<>(allCaseEntities));

        Page<CaseDTO> models = caseService.searchBy(null, caseEntity.getCourt().getId(), null);
        assertThat(models.getSize()).isEqualTo(1);
        assertThat(models.get().toList().getFirst().getId()).isEqualTo(caseEntity.getId());
        assertThat(models.get().toList().getFirst().getCourt().getId()).isEqualTo(caseEntity.getCourt().getId());
    }

    @DisplayName("Find all cases and return list of models where case with court is in list")
    @Test
    void findAllCourtIdParamNotFoundSuccess() {
        UUID uuid = UUID.randomUUID();
        when(caseRepository.searchCasesBy(null, uuid, null)).thenReturn(Page.empty());

        Page<CaseDTO> models = caseService.searchBy(null, uuid, null);
        assertThat(models.getSize()).isEqualTo(0);
    }

    @Test
    void createSuccess() {
        Case testingCase = createTestingCase();
        var caseDTOModel = new CreateCaseDTO(testingCase);

        when(courtRepository.findById(testingCase.getCourt().getId())).thenReturn(
            Optional.of(testingCase.getCourt()));
        when(caseRepository.findById(testingCase.getId())).thenReturn(Optional.empty());

        caseService.upsert(caseDTOModel);

        verify(courtRepository, times(1)).findById(caseDTOModel.getCourtId());
        verify(caseRepository, times(1)).findById(caseDTOModel.getId());
        verify(caseRepository, times(1)).save(any());
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
        when(caseRepository.existsByIdAndDeletedAtIsNull(caseEntity.getId())).thenReturn(true);

        caseService.deleteById(caseEntity.getId());

        verify(caseRepository, times(1)).existsByIdAndDeletedAtIsNull(caseEntity.getId());
        verify(caseRepository, times(1)).deleteById(caseEntity.getId());
    }

    @Test
    void deleteByIdNotFound() {
        UUID caseId = UUID.randomUUID();
        when(caseRepository.existsByIdAndDeletedAtIsNull(caseId)).thenReturn(false);

        assertThrows(NotFoundException.class, () -> caseService.deleteById(caseId));

        verify(caseRepository, times(1)).existsByIdAndDeletedAtIsNull(caseId);
        verify(caseRepository, never()).deleteById(caseId);
    }
}
