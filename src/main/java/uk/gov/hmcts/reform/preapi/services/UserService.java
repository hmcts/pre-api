package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;

import java.util.UUID;

@Service
public class UserService {

    private final AppAccessRepository appAccessRepository;

    public UserService(AppAccessRepository appAccessRepository) {
        this.appAccessRepository = appAccessRepository;
    }

    @Transactional
    public UserDTO findById(UUID userId) {
        return appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .map(UserDTO::new)
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }
}
