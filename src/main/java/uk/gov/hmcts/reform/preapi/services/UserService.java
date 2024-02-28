package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.AppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService {

    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;

    private final AppAccessService appAccessService;

    @Autowired
    public UserService(AppAccessRepository appAccessRepository,
                       CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       UserRepository userRepository,
                       PortalAccessRepository portalAccessRepository,
                       AppAccessService appAccessService) {
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.portalAccessRepository = portalAccessRepository;
        this.appAccessService = appAccessService;
    }

    @Transactional
    public UserDTO findById(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .map(UserDTO::new)
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }

    @Transactional
    public List<AppAccessDTO> findByEmail(String email) {
        var access = appAccessRepository.findAllByUser_EmailIgnoreCaseAndDeletedAtNullAndUser_DeletedAtNull(email)
            .stream()
            .map(AppAccessDTO::new)
            .toList();

        if (access.isEmpty()) {
            throw new NotFoundException("User: " + email);
        }
        return access;
    }

    @Transactional
    @PreAuthorize("!#includeDeleted or @authorisationService.canViewDeleted(authentication)")
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public Page<UserDTO> findAllBy(
        String firstName,
        String lastName,
        String email,
        String organisation,
        UUID court,
        UUID role,
        AccessType accessType,
        boolean includeDeleted,
        Pageable pageable
    ) {
        if (court != null && !courtRepository.existsById(court)) {
            throw new NotFoundException("Court: " + court);
        }

        if (role != null && !roleRepository.existsById(role)) {
            throw new NotFoundException("Role: " + role);
        }

        return userRepository.searchAllBy(
            firstName,
            lastName,
            email,
            organisation,
            court,
            role,
            accessType == AccessType.PORTAL,
            accessType == AccessType.APP,
            includeDeleted,
            pageable
        ).map(UserDTO::new);
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateUserDTO createUserDTO) {
        var user = userRepository.findById(createUserDTO.getId());
        if (user.isPresent() && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createUserDTO.getId().toString());
        }

        var entity = user.orElse(new User());
        entity.setId(createUserDTO.getId());
        entity.setFirstName(createUserDTO.getFirstName());
        entity.setLastName(createUserDTO.getLastName());
        entity.setEmail(createUserDTO.getEmail());
        entity.setPhone(createUserDTO.getPhoneNumber());
        entity.setOrganisation(createUserDTO.getOrganisation());
        userRepository.saveAndFlush(entity);

        var isUpdate = user.isPresent();
        if (isUpdate) {
            entity
                .getAppAccess()
                .stream()
                .map(AppAccess::getId)
                .filter(id -> createUserDTO.getAppAccess().stream().map(CreateAppAccessDTO::getId)
                    .noneMatch(newAccessId -> newAccessId == id))
                .forEach(appAccessRepository::deleteById);
        }

        createUserDTO.getAppAccess().forEach(appAccessService::upsert);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateInviteDTO createInviteDTO) {
        var user = userRepository.findById(createInviteDTO.getUserId());
        if (user.isPresent() && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createInviteDTO.getUserId().toString());
        } else if (user.isPresent() && portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(createInviteDTO.getUserId())
            .isPresent()) {
            return UpsertResult.UPDATED;
        }

        var userEntity = user.orElse(new User());

        userEntity.setId(createInviteDTO.getUserId());
        userEntity.setFirstName(createInviteDTO.getFirstName());
        userEntity.setLastName(createInviteDTO.getLastName());
        userEntity.setEmail(createInviteDTO.getEmail());
        userEntity.setOrganisation(createInviteDTO.getOrganisation());
        userEntity.setPhone(createInviteDTO.getPhone());
        userRepository.save(userEntity);

        var portalAccessEntity = portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(createInviteDTO.getUserId())
            .orElse(new PortalAccess());

        portalAccessEntity.setUser(userEntity);
        portalAccessEntity.setStatus(AccessStatus.INVITATION_SENT);
        portalAccessEntity.setCode(createInviteDTO.getCode());
        portalAccessEntity.setInvitedAt(Timestamp.from(java.time.Instant.now()));
        portalAccessRepository.save(portalAccessEntity);

        return UpsertResult.CREATED;
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
            .ifPresent(appAccess -> {
                appAccess.setActive(false);
                appAccess.setDeletedAt(Timestamp.from(Instant.now()));
                appAccessRepository.save(appAccess);
            });

        userRepository.deleteById(userId);
    }

    @Transactional
    public void undelete(UUID id) {
        var entity = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User: " + id));
        if (!entity.isDeleted()) {
            return;
        }
        entity.setDeletedAt(null);
        userRepository.save(entity);

        appAccessRepository
            .findAllByUser_IdAndDeletedAtIsNotNull(id)
            .forEach(a -> {
                a.setDeletedAt(null);
                a.setActive(true);
                appAccessRepository.save(a);
            });

        portalAccessRepository
            .findAllByUser_IdAndDeletedAtIsNotNull(id)
            .forEach(p -> {
                p.setDeletedAt(null);
                portalAccessRepository.save(p);
            });
    }
}
