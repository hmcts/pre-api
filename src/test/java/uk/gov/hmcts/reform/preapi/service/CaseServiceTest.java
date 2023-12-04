package uk.gov.hmcts.reform.preapi.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;

import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CaseService.class)
class CaseServiceTest {

    private static uk.gov.hmcts.reform.preapi.entities.Case caseEntity;

    @MockBean
    private CaseRepository caseRepository;

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
        caseEntity.setDeletedAt(null);
        caseEntity.setCreatedAt(Timestamp.from(java.time.Instant.now()));
        caseEntity.setModifiedAt(Timestamp.from(java.time.Instant.now()));
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
}
