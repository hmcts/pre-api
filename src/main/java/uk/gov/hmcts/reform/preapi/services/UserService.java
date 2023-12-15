package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.UUID;

@Service
public class UserService {

    private final AppAccessRepository appAccessRepository;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public UserService(AppAccessRepository appAccessRepository,
                       UserRepository userRepository,
                       PortalAccessRepository portalAccessRepository) {
        this.appAccessRepository = appAccessRepository;
        this.userRepository = userRepository;
        this.portalAccessRepository = portalAccessRepository;
    }

    @Transactional
    public UserDTO findById(UUID userId) {
        return appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .map(UserDTO::new)
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }

    @Transactional
    public void deleteById(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new NotFoundException("User: " + userId);
        }

        portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .ifPresent(portalAccess -> portalAccessRepository.deleteById(portalAccess.getId()));

        appAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .ifPresent(appAccess -> appAccessRepository.deleteById(appAccess.getId()));

        userRepository.deleteById(userId);
    }
}
