package uk.gov.hmcts.reform.preapi.services;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = CourtService.class)
public class CourtServiceTest {
    private static Court courtEntity;

    @MockBean
    private CourtRepository courtRepository;

    @Autowired
    private CourtService courtService;

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
            courtRepository.searchBy(null, null, null)
        ).thenReturn(List.of(courtEntity));

        var models = courtService.findAllBy(null, null, null, null);
        assertThat(models.size()).isEqualTo(1);

        var first = models.get(0);
        assertThat(first.getId()).isEqualTo(courtEntity.getId());
        assertThat(first.getName()).isEqualTo(courtEntity.getName());
        assertThat(first.getLocationCode()).isEqualTo(courtEntity.getLocationCode());
        assertThat(first.getCourtType()).isEqualTo(courtEntity.getCourtType());
    }

    @DisplayName("Find all courts when region name is filtered and return a list of model")
    @Test
    void findAllCourtsRegionSearchSuccess() {
        when(
            courtRepository.searchBy(null, null, null)
        ).thenReturn(List.of(courtEntity));

        var models = courtService.findAllBy(null, null, null, "example");
        assertThat(models.size()).isEqualTo(1);

        var first = models.get(0);
        assertThat(first.getId()).isEqualTo(courtEntity.getId());
        assertThat(first.getName()).isEqualTo(courtEntity.getName());
        assertThat(first.getLocationCode()).isEqualTo(courtEntity.getLocationCode());
        assertThat(first.getCourtType()).isEqualTo(courtEntity.getCourtType());

        assertThat(first.getRegions().size()).isEqualTo(1);
        var region = first.getRegions().stream().findFirst().get();
        assertThat(region).isNotNull();
        assertThat(region.getName()).isEqualTo("Example Region");
    }

    @DisplayName("Find all courts when region name is filtered and return a list of model")
    @Test
    void findAllCourtsRegionSearchEmptySuccess() {
        when(
            courtRepository.searchBy(null, null, null)
        ).thenReturn(List.of(courtEntity));

        var models = courtService.findAllBy(null, null, null, "invalid region");
        assertThat(models.size()).isEqualTo(0);
    }
}
