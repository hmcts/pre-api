package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NoDuplicateCourtsValidatorTest {
    private NoDuplicateCourtsValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new NoDuplicateCourtsValidator();
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

    @DisplayName("Should be valid when app access set does not contain access for the same court multiple times")
    @Test
    void shouldBeValidWithoutAccessToSameCourtMultipleTimes() {
        assertTrue(validator.isValid(Set.of(createAppAccessDTO(), createAppAccessDTO()), context));
    }

    @DisplayName("Should be invalid when app access set contains access for the same court multiple times")
    @Test
    void shouldBeInvalidWithAccessToSameCourtMultipleTimes() {
        var duplicate1 = createAppAccessDTO();
        var duplicate2 = createAppAccessDTO();
        duplicate2.setCourtId(duplicate1.getCourtId());

        assertFalse(validator.isValid(
            Set.of(duplicate1, duplicate2, createAppAccessDTO()),
            context
        ));
    }

    private CreateAppAccessDTO createAppAccessDTO() {
        var dto = new CreateAppAccessDTO();
        dto.setId(UUID.randomUUID());
        dto.setCourtId(UUID.randomUUID());
        return dto;
    }
}
