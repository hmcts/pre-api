package uk.gov.hmcts.reform.preapi.user.service;

import uk.gov.hmcts.reform.preapi.entities.User;

import java.util.Optional;
import java.util.UUID;


public interface UserService {

    Optional<User> findById(UUID userId);
}
