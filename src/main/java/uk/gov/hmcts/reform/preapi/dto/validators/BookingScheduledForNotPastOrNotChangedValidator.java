package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.repositories.BookingRepository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class BookingScheduledForNotPastOrNotChangedValidator
    implements ConstraintValidator<BookingScheduledForNotPastOrNotChangedConstraint, CreateBookingDTO> {

    private final BookingRepository bookingRepository;

    @Autowired
    public BookingScheduledForNotPastOrNotChangedValidator(final BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Override
    public void initialize(BookingScheduledForNotPastOrNotChangedConstraint constraintAnnotation) {
    }

    @Override
    public boolean isValid(CreateBookingDTO booking, ConstraintValidatorContext cxt) {
        if (booking == null) {
            return true;
        }

        if (booking.getScheduledFor() == null) {
            cxt.disableDefaultConstraintViolation();
            cxt.buildConstraintViolationWithTemplate("scheduled_for is required and must not be before today")
                .addPropertyNode("scheduledFor")
                .addConstraintViolation();
            return false;
        }

        var localDateField = toLocalDate(booking.getScheduledFor());
        var today = LocalDate.now();

        return !localDateField.isBefore(today)
            || bookingRepository.findById(booking.getId())
                .map(b -> {
                    if (toLocalDate(b.getScheduledFor()).equals(localDateField)) {
                        return true;
                    } else {
                        cxt.disableDefaultConstraintViolation();
                        cxt.buildConstraintViolationWithTemplate("must not be before today")
                            .addPropertyNode("scheduledFor")
                            .addConstraintViolation();
                        return false;
                    }
                })
            .orElseGet(
                () -> {
                    cxt.disableDefaultConstraintViolation();
                    cxt.buildConstraintViolationWithTemplate("must not be before today")
                        .addPropertyNode("scheduledFor")
                        .addConstraintViolation();
                    return false;
                });
    }

    private LocalDate toLocalDate(Timestamp timestamp) {
        return LocalDateTime.ofInstant(timestamp.toInstant(), ZoneId.of("Europe/London"))
            .toLocalDate();
    }
}
