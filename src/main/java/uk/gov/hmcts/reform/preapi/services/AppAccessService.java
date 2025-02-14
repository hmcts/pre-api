package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
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
        RoleRepository roleRepository
    ) {
        this.appAccessRepository = appAccessRepository;
        this.userRepository = userRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
    }

    @Transactional
    public UpsertResult upsert(CreateAppAccessDTO createAppAccessDTO) {
        var appAccess = appAccessRepository
            .findById(createAppAccessDTO.getId());

        if (appAccess.isPresent() && appAccess.get().isDeleted()) {
            throw new ResourceInDeletedStateException("AppAccessDTO", createAppAccessDTO.getId().toString());
        }

        var user = userRepository.findByIdAndDeletedAtIsNull(createAppAccessDTO.getUserId())
            .orElseThrow(() -> new NotFoundException("User: " + createAppAccessDTO.getUserId()));
        var court = courtRepository.findById(createAppAccessDTO.getCourtId())
            .orElseThrow(() -> new NotFoundException("Court: " + createAppAccessDTO.getCourtId()));
        var role = roleRepository.findById(createAppAccessDTO.getRoleId())
            .orElseThrow(() -> new NotFoundException("Role: " + createAppAccessDTO.getRoleId()));

        var entity = appAccess.orElse(new AppAccess());
        entity.setId(createAppAccessDTO.getId());
        entity.setUser(user);
        entity.setCourt(court);
        entity.setRole(role);
        // TODO remove if statement when uncommented @NotNull on CreateAppAccessDTO.courtAccessType
        if (createAppAccessDTO.getDefaultCourt() == null) {
            createAppAccessDTO.setDefaultCourt(true);
        }
        entity.setDefaultCourt(createAppAccessDTO.getDefaultCourt());

        if (createAppAccessDTO.getActive() != null) {
            entity.setActive(createAppAccessDTO.getActive());
        }
        entity.setLastAccess(createAppAccessDTO.getLastActive());
        appAccessRepository.saveAndFlush(entity);

        var isUpdate = appAccess.isPresent();
        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
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
}
