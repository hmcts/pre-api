package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AppAccessService {

    private final AppAccessRepository appAccessRepository;
    private final UserRepository userRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;

    @Autowired
    public AppAccessService(
        AppAccessRepository appAccessRepository,
        UserRepository userRepository,
        CourtRepository courtRepository,
        RoleRepository roleRepository) {
        this.appAccessRepository = appAccessRepository;
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
    }

    public Optional<AppAccess> getLatestAccessByCourtIdAndUserId(UUID courtId, UUID userId) {
        // The database does not enforce a unique constraint on courtId and userId.
        // Attempting to find the most relevant app access for this user and court
        return appAccessRepository.findAllByCourtIdAndUserId(courtId, userId)
            .stream()
            .min(AppAccess.BY_ACTIVE_LAST_ACCESS_ROLE);
    }

    @Transactional
    public UpsertResult upsert(CreateAppAccessDTO createAppAccessDTO) {
        Optional<AppAccess> latestAccessForThisUserCourt =
            getLatestAccessByCourtIdAndUserId(
                createAppAccessDTO.getCourtId(),
                createAppAccessDTO.getUserId()
            );

        AppAccess entity;
        if (latestAccessForThisUserCourt.isEmpty()) {
            entity = new AppAccess();
            entity.setId(UUID.randomUUID());

            User user = userRepository.findByIdAndDeletedAtIsNull(createAppAccessDTO.getUserId())
                .orElseThrow(() -> new NotFoundException("User: " + createAppAccessDTO.getUserId()));
            entity.setUser(user);

            Court court = courtRepository.findById(createAppAccessDTO.getCourtId())
                .orElseThrow(() -> new NotFoundException("Court: " + createAppAccessDTO.getCourtId()));
            entity.setCourt(court);
        } else {
            entity = latestAccessForThisUserCourt.get();
        }

        // TODO remove if statement when uncommented @NotNull on CreateAppAccessDTO.courtAccessType
        if (createAppAccessDTO.getDefaultCourt() == null) {
            createAppAccessDTO.setDefaultCourt(true);
        }
        entity.setDefaultCourt(createAppAccessDTO.getDefaultCourt());

        if (createAppAccessDTO.getActive() != null) {
            entity.setActive(createAppAccessDTO.getActive());
        }

        if (entity.isActive()) {
            entity.setDeletedAt(null);
        } else {
            entity.setDeletedAt(Timestamp.from(Instant.now()));
        }

        Role role = roleRepository.findById(createAppAccessDTO.getRoleId())
            .orElseThrow(() -> new NotFoundException("Role: " + createAppAccessDTO.getRoleId()));
        entity.setRole(role);

        appAccessRepository.save(entity);

        return latestAccessForThisUserCourt.isPresent() ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAppAccessIDsForUserId(UUID userId) {
        // Enables superuser to reset app access ID if compromised
        appAccessRepository.findAllByUserId(userId)
            .forEach(access -> {
                access.setId(UUID.randomUUID());
                appAccessRepository.save(access);
            });
    }

    @Transactional
    public void deleteDuplicateAccessForUserIdAndCourtId(UUID userId, UUID courtId) {
        Optional<AppAccess> latestAccess = getLatestAccessByCourtIdAndUserId(courtId, userId);
        appAccessRepository.findAllByCourtIdAndUserId(courtId, userId)
            .stream()
            .filter(access -> !access.getId().equals(latestAccess.map(AppAccess::getId).orElse(null)))
            .forEach(access -> {
                access.setActive(false);
                access.setDeletedAt(Timestamp.from(Instant.now()));
                appAccessRepository.save(access);
            });
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteById(UUID appId) {
        appAccessRepository
            .findById(appId)
            .ifPresent(
                access -> {
                    access.setActive(false);
                    access.setDeletedAt(Timestamp.from(Instant.now()));
                    appAccessRepository.save(access);
                });
    }

    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void deleteByUserIdAndCourtId(UUID userId, UUID courtId) {
        appAccessRepository
            .findAllByCourtIdAndUserId(courtId, userId)
            .forEach(
                access -> {
                    access.setActive(false);
                    access.setDeletedAt(Timestamp.from(Instant.now()));
                    appAccessRepository.save(access);
                });
    }

    @Transactional
    public void deleteByUserId(UUID userId) {
        appAccessRepository
            .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .forEach(access -> {
                access.setActive(false);
                access.setDeletedAt(Timestamp.from(Instant.now()));
                appAccessRepository.save(access);
            });
    }

    @Transactional
    public void undeleteByUserId(UUID userId) {
        appAccessRepository
            .findAllByUser_IdAndDeletedAtIsNotNull(userId)
            .forEach(a -> {
                a.setDeletedAt(null);
                a.setActive(true);
                appAccessRepository.save(a);
            });
    }
}
