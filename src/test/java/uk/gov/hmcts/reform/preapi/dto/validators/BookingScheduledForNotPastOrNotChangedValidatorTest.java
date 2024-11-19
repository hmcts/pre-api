package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BookingScheduledForNotPastOrNotChangedValidatorTest {

    private BookingRepository bookingRepository;
    private BookingScheduledForNotPastOrNotChangedValidator validator;

    @BeforeEach
    public void setUp() {
        bookingRepository = mock(BookingRepository.class);
        validator = new BookingScheduledForNotPastOrNotChangedValidator(bookingRepository);
    }

    @Test
    public void isValidIsNullTrue() {
        var context = mock(ConstraintValidatorContext.class);

        assertThat(validator.isValid(null, context)).isTrue();
    }

    @Test
    public void isValidBookingScheduledForNullFalse() {
        var dto = new CreateBookingDTO();
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);

        assertThat(validator.isValid(dto, context)).isFalse();

        verify(context).disableDefaultConstraintViolation();
        verify(builder).addPropertyNode("scheduledFor");
        verify(nodeBuilder).addConstraintViolation();
    }

    @Test
    public void isValidScheduledForFutureTrue() {
        var dto = new CreateBookingDTO();
        dto.setScheduledFor(Timestamp.from(Instant.now().plusSeconds(604800))); // one week in future
        var context = mock(ConstraintValidatorContext.class);

        assertThat(validator.isValid(dto, context)).isTrue();
    }

    @Test
    public void isValidScheduledForPastBookingNotFoundFalse() {
        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setScheduledFor(Timestamp.from(Instant.now().minusSeconds(604800))); // one week in past
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
        when(bookingRepository.findById(dto.getId())).thenReturn(Optional.empty());

        assertThat(validator.isValid(dto, context)).isFalse();

        verify(bookingRepository, times(1)).findById(dto.getId());
        verify(context, times(1)).disableDefaultConstraintViolation();
        verify(builder, times(1)).addPropertyNode("scheduledFor");
        verify(nodeBuilder, times(1)).addConstraintViolation();
    }

    @Test
    public void isValidScheduledForPastBookingExistsScheduledForChangedFalse() {
        var booking = new Booking();
        booking.setScheduledFor(Timestamp.from(Instant.now()));

        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setScheduledFor(Timestamp.from(Instant.now().minusSeconds(604800))); // one week in past
        var context = mock(ConstraintValidatorContext.class);
        var builder = mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        var nodeBuilder =
            mock(ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addPropertyNode(anyString())).thenReturn(nodeBuilder);
        when(bookingRepository.findById(dto.getId())).thenReturn(Optional.of(booking));

        assertThat(validator.isValid(dto, context)).isFalse();

        verify(bookingRepository, times(1)).findById(dto.getId());
        verify(context, times(1)).disableDefaultConstraintViolation();
        verify(builder, times(1)).addPropertyNode("scheduledFor");
        verify(nodeBuilder, times(1)).addConstraintViolation();
    }

    @Test
    public void isValidScheduledForPastBookingExistsScheduledForNotChangedTrue() {
        var booking = new Booking();
        booking.setScheduledFor(Timestamp.from(Instant.now().minusSeconds(604800))); // one week in past
        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setScheduledFor(booking.getScheduledFor());
        var context = mock(ConstraintValidatorContext.class);

        when(bookingRepository.findById(dto.getId())).thenReturn(Optional.of(booking));

        assertThat(validator.isValid(dto, context)).isTrue();

        verify(bookingRepository, times(1)).findById(dto.getId());
    }
}
