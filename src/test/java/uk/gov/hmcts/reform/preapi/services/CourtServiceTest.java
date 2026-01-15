package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.base.PreApiController;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RegionRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CourtService.class)
public class CourtServiceTest {
    private static Court courtEntity;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private RegionRepository regionRepository;

    @Autowired
    private CourtService courtService;

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeAll
    static void setUp() {
        courtEntity = new Court();
        courtEntity.setId(UUID.randomUUID());
        courtEntity.setCourtType(CourtType.CROWN);
        courtEntity.setName("Example Court");
        courtEntity.setLocationCode("123456");

        var region = new Region();
        region.setId(UUID.randomUUID());
        region.setName("Example Region");

        courtEntity.setRegions(Set.of(region));
    }

    @DisplayName("Find a court by it's id and return a model")
    @Test
    void findCourtByIdSuccess() {
        when(
            courtRepository.findById(courtEntity.getId())
        ).thenReturn(Optional.of(courtEntity));

        var model = courtService.findById(courtEntity.getId());
        assertThat(model.getId()).isEqualTo(courtEntity.getId());
        assertThat(model.getName()).isEqualTo(courtEntity.getName());
        assertThat(model.getLocationCode()).isEqualTo(courtEntity.getLocationCode());
        assertThat(model.getCourtType()).isEqualTo(courtEntity.getCourtType());
    }

    @DisplayName("Find a court by it's id which doesn't exist")
    @Test
    void findCourtByIdNotFound() {
        assertThrows(
            NotFoundException.class,
            () -> courtService.findById(courtEntity.getId())
        );

        verify(courtRepository, times(1)).findById(courtEntity.getId());
    }

    @DisplayName("Find all courts and return a list of models")
    @Test
    void findAllCourtsSuccess() {
        when(
            courtRepository.searchBy(null, null, null, null, pageable)
        ).thenReturn(new PageImpl<>(List.of(courtEntity)));

        var models = courtService.findAllBy(null, null, null, null, pageable);
        assertThat(models.getTotalElements()).isEqualTo(1);

        var first = models.get().toList().getFirst();
        assertThat(first.getId()).isEqualTo(courtEntity.getId());
        assertThat(first.getName()).isEqualTo(courtEntity.getName());
        assertThat(first.getLocationCode()).isEqualTo(courtEntity.getLocationCode());
        assertThat(first.getCourtType()).isEqualTo(courtEntity.getCourtType());
    }

    @DisplayName("Create a court")
    @Test
    void createCourtSuccess() {
        var courtModel = new CreateCourtDTO();
        courtModel.setId(UUID.randomUUID());
        courtModel.setName("Example Court");
        courtModel.setCourtType(CourtType.CROWN);
        courtModel.setLocationCode("1234567890");

        var courtEntity = new Court();

        when(regionRepository.existsById(any())).thenReturn(true);
        when(courtRepository.findById(courtModel.getId())).thenReturn(Optional.empty());
        when(courtRepository.save(courtEntity)).thenReturn(courtEntity);

        assertThat(courtService.upsert(courtModel)).isEqualTo(UpsertResult.CREATED);
    }

    @DisplayName("Update a court")
    @Test
    void updateCourtSuccess() {
        var courtModel = new CreateCourtDTO();
        courtModel.setId(UUID.randomUUID());
        courtModel.setName("Example Court");
        courtModel.setCourtType(CourtType.CROWN);
        courtModel.setLocationCode("1234567890");

        var courtEntity = new Court();

        when(regionRepository.existsById(any())).thenReturn(true);
        when(courtRepository.findById(courtModel.getId())).thenReturn(Optional.of(courtEntity));
        when(courtRepository.save(courtEntity)).thenReturn(courtEntity);

        assertThat(courtService.upsert(courtModel)).isEqualTo(UpsertResult.UPDATED);
    }

    @DisplayName("Create/update a court with an invalid region")
    @Test
    void updateCourtRegionBadRequest() {
        var regionId = UUID.randomUUID();
        var courtModel = new CreateCourtDTO();
        courtModel.setId(UUID.randomUUID());
        courtModel.setName("Example Court");
        courtModel.setCourtType(CourtType.CROWN);
        courtModel.setLocationCode("1234567890");
        courtModel.setRegions(List.of(regionId));

        when(regionRepository.existsById(regionId)).thenReturn(false);

        assertThrows(
            NotFoundException.class,
            () -> courtService.upsert(courtModel)
        );
    }

    @DisplayName("Should update court addresses")
    @Test
    void updateCourtEmailAddressesSuccess() {
        final String fileContents = """
Region,Court,PRE Inbox Address
South East,Example Court,PRE.Edits.Example@justice.gov.uk
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "email_addresses.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        when(courtRepository.findFirstByName("Example Court")).thenReturn(Optional.of(courtEntity));
        when(courtRepository.findAllBy()).thenReturn(List.of(courtEntity));

        courtService.updateCourtEmails(file);

        ArgumentCaptor<Court> updatedCourtSavedInDb = ArgumentCaptor.forClass(Court.class);

        verify(courtRepository, times(1)).save(updatedCourtSavedInDb.capture());
        assertThat(updatedCourtSavedInDb.getValue().getGroupEmail())
            .isEqualTo("PRE.Edits.Example@justice.gov.uk");
    }

    @DisplayName("Should ignore blank lines in CSV file when updating court email addresses")
    @Test
    void updateCourtEmailAddressesIgnoreBlanks() {
        final String fileContents = """
Region,Court,PRE Inbox Address
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "email_addresses.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        when(courtRepository.findFirstByName("Example Court")).thenReturn(Optional.of(courtEntity));
        when(courtRepository.findAllBy()).thenReturn(List.of(courtEntity));

        courtService.updateCourtEmails(file);

        verify(courtRepository, times(0)).save(any());
    }

    @DisplayName("Should throw an error if court does not exist when updating court email addresses")
    @Test
    void updateCourtEmailAddressesThrowErrorForInvalidCourt() {
        final String fileContents = """
Region,Court,PRE Inbox Address
South East,Example Court,PRE.Edits.Example@justice.gov.uk
            """;

        MockMultipartFile file = new MockMultipartFile(
            "file", "email_addresses.csv",
            PreApiController.CSV_FILE_TYPE, fileContents.getBytes()
        );

        when(courtRepository.findFirstByName("Example Court")).thenReturn(Optional.empty());
        when(courtRepository.findAllBy()).thenReturn(List.of(courtEntity));

        courtService.updateCourtEmails(file);

        assertThrows(
            NotFoundException.class,
            () -> courtService.updateCourtEmails(file)
        );

        verifyNoInteractions(courtRepository);
    }
}
