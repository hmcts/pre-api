package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtAccessType;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrimaryCourtValidatorTest {
    private PrimaryCourtValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new PrimaryCourtValidator();
    }

    @DisplayName("Should be valid when app access set is null")
    @Test
    void shouldBeValidWhenAppAccessSetIsNull() {
        assertTrue(validator.isValid(null, context));
    }

    @DisplayName("Should be valid when app access set is empty")
    @Test
    void shouldBeValidWhenAppAccessSetIsEmpty() {
        assertTrue(validator.isValid(Set.of(), context));
    }

    @DisplayName("Should handle court access type null values as PRIMARY court assignments")
    @Test
    void shouldHandleCourtAccessTypeNullValues() {
        var dto = createAppAccessDTO(null);

        assertTrue(validator.isValid(Set.of(dto), context));
        assertThat(dto.getCourtAccessType()).isEqualTo(CourtAccessType.PRIMARY);
    }

    @DisplayName("Should be valid when set contains only one PRIMARY court assignment")
    @Test
    void shouldBeValidOnePrimaryCourt() {
        assertTrue(validator.isValid(Set.of(createAppAccessDTO(null)), context));
        assertTrue(validator.isValid(Set.of(createAppAccessDTO(CourtAccessType.PRIMARY)), context));
    }

    @DisplayName("Should be invalid when set contains multiple PRIMARY court assignments")
    @Test
    void shouldBeInvalidMultiplePrimaryCourts() {
        assertFalse(validator.isValid(
            Set.of(createAppAccessDTO(CourtAccessType.PRIMARY), createAppAccessDTO(CourtAccessType.PRIMARY)), context
        ));
        assertFalse(validator.isValid(
            Set.of(createAppAccessDTO(CourtAccessType.PRIMARY), createAppAccessDTO(null)), context
        ));
        assertFalse(validator.isValid(
            Set.of(createAppAccessDTO(null), createAppAccessDTO(null)), context
        ));
    }

    @DisplayName("Should be valid when set contains one PRIMARY and many SECONDARY court assignments (up to maximum)")
    @Test
    void shouldBeValidOnePrimaryAndMultipleSecondaryCourts() {
        var primary = createAppAccessDTO(CourtAccessType.PRIMARY);
        assertTrue(validator.isValid(
            Set.of(primary, createAppAccessDTO(CourtAccessType.SECONDARY)),
            context
        ));

        var set = IntStream.range(0, PrimaryCourtValidator.MAXIMUM_SECONDARY_COURTS)
            .mapToObj(i -> createAppAccessDTO(CourtAccessType.SECONDARY))
            .collect(Collectors.toSet());
        set.add(primary);

        assertTrue(validator.isValid(
            set,
            context
        ));
    }

    @DisplayName("Should be invalid when there are too many SECONDARY court assignments")
    @Test
    void shouldBeInvalidMultipleSecondaryCourts() {
        var set = IntStream.range(0, PrimaryCourtValidator.MAXIMUM_SECONDARY_COURTS + 1)
            .mapToObj(i -> createAppAccessDTO(CourtAccessType.SECONDARY))
            .collect(Collectors.toSet());
        set.add(createAppAccessDTO(CourtAccessType.PRIMARY));

        assertFalse(validator.isValid(
            set,
            context
        ));
    }

    private CreateAppAccessDTO createAppAccessDTO(CourtAccessType type) {
        var dto = new CreateAppAccessDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtAccessType(type);
        return dto;
    }
}
