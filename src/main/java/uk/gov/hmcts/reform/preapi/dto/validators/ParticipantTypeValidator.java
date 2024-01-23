package uk.gov.hmcts.reform.preapi.dto.validators;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.util.Set;

public class ParticipantTypeValidator
    implements ConstraintValidator<ParticipantTypeConstraint, Set<CreateParticipantDTO>> {
    @Override
    public void initialize(ParticipantTypeConstraint participantTypeConstraint) {
    }

    @Override
    public boolean isValid(Set<CreateParticipantDTO> participants, ConstraintValidatorContext cxt) {
        if (participants == null) {
            return false;
        }
        return participants.stream()
            .anyMatch(participant -> participant.getParticipantType().equals(ParticipantType.WITNESS))
            && participants.stream()
            .anyMatch(participant -> participant.getParticipantType().equals(ParticipantType.DEFENDANT));
    }
}
