package uk.gov.hmcts.reform.preapi.services;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class UserService {

    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;
    private final AppAccessService appAccessService;
    private final PortalAccessService portalAccessService;
    private final TermsAndConditionsRepository termsAndConditionsRepository;

    @Autowired
    public UserService(AppAccessRepository appAccessRepository,
                       CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       UserRepository userRepository,
                       PortalAccessRepository portalAccessRepository,
                       AppAccessService appAccessService,
                       PortalAccessService portalAccessService,
                       TermsAndConditionsRepository termsAndConditionsRepository) {
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.portalAccessRepository = portalAccessRepository;
        this.appAccessService = appAccessService;
        this.portalAccessService = portalAccessService;
        this.termsAndConditionsRepository = termsAndConditionsRepository;
    }

    @Transactional()
    public UserDTO findById(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .map(user ->
                     new UserDTO(
                         user,
                         getAllLatestTermsAndConditions()
                     ))
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }

    @Transactional
    public AccessDTO findByEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
            .map(access ->
                     new AccessDTO(
                            access,
                            getAllLatestTermsAndConditions()
                     ))
            .orElseThrow(() -> new NotFoundException("User: " + email));
    }

    @Transactional
    @PreAuthorize("!#includeDeleted or @authorisationService.canViewDeleted(authentication)")
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public Page<UserDTO> findAllBy(
        String name,
        String email,
        String organisation,
        UUID court,
        UUID role,
        AccessType accessType,
        boolean includeDeleted,
        Boolean isAppActive,
        Pageable pageable
    ) {
        if (court != null && !courtRepository.existsById(court)) {
            throw new NotFoundException("Court: " + court);
        }

        if (role != null && !roleRepository.existsById(role)) {
            throw new NotFoundException("Role: " + role);
        }

        Set<TermsAndConditions> allLatestTermsAndConditions = getAllLatestTermsAndConditions();

        return userRepository.searchAllBy(
            name,
            email,
            organisation,
            court,
            role,
            accessType == AccessType.PORTAL,
            accessType == AccessType.APP,
            includeDeleted,
            isAppActive,
            pageable
        ).map(user -> new UserDTO(user, allLatestTermsAndConditions));
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateUserDTO createUserDTO) {
        Optional<User> user = userRepository.findById(createUserDTO.getId());

        boolean isUpdate = user.isPresent();
        if (isUpdate && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createUserDTO.getId().toString());
        }

        if (!isUpdate && userRepository.existsByEmailIgnoreCase(createUserDTO.getEmail())) {
            throw new ConflictException("User with email: " + createUserDTO.getEmail() + " already exists");
        }

        createUserDTO.getPortalAccess().stream().map(CreatePortalAccessDTO::getId).forEach(id -> {
            if (!portalAccessRepository.existsById(id)) {
                throw new NotFoundException("Portal Access: " + id);
            }
        });

        User entity = user.orElse(new User());
        entity.setId(createUserDTO.getId());
        entity.setFirstName(createUserDTO.getFirstName());
        entity.setLastName(createUserDTO.getLastName());
        entity.setEmail(createUserDTO.getEmail());
        entity.setPhone(createUserDTO.getPhoneNumber());
        entity.setOrganisation(createUserDTO.getOrganisation());
        userRepository.saveAndFlush(entity);

        if (isUpdate) {
            Stream.ofNullable(entity.getAppAccess())
                .flatMap(Collection::stream)
                .filter(appAccess -> appAccess.getDeletedAt() == null)
                .map(AppAccess::getId)
                .filter(id -> createUserDTO.getAppAccess().stream().map(CreateAppAccessDTO::getId)
                    .noneMatch(newAccessId -> newAccessId.equals(id)))
                .forEach(appAccessService::deleteById);

            Stream.ofNullable(entity.getPortalAccess())
                .flatMap(Collection::stream)
                .map(PortalAccess::getId)
                .filter(id -> createUserDTO.getPortalAccess().stream().map(CreatePortalAccessDTO::getId)
                    .noneMatch(newAccessId -> newAccessId.equals(id)))
                .forEach(portalAccessService::deleteById);

            createUserDTO.getPortalAccess().forEach(portalAccessService::update);
        }

        createUserDTO.getAppAccess().forEach(appAccessService::upsert);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateInviteDTO createInviteDTO) {
        Optional<User> user = userRepository.findById(createInviteDTO.getUserId());
        if (user.isPresent() && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createInviteDTO.getUserId().toString());
        } else if (user.isPresent() && portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(createInviteDTO.getUserId())
            .isPresent()) {
            return UpsertResult.UPDATED;
        }

        User userEntity = user.orElse(new User());

        userEntity.setId(createInviteDTO.getUserId());
        userEntity.setFirstName(createInviteDTO.getFirstName());
        userEntity.setLastName(createInviteDTO.getLastName());
        userEntity.setEmail(createInviteDTO.getEmail());
        userEntity.setOrganisation(createInviteDTO.getOrganisation());
        userEntity.setPhone(createInviteDTO.getPhone());
        userRepository.save(userEntity);

        PortalAccess portalAccessEntity = portalAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(createInviteDTO.getUserId())
            .orElse(new PortalAccess());

        portalAccessEntity.setUser(userEntity);
        portalAccessEntity.setStatus(AccessStatus.INVITATION_SENT);
        portalAccessEntity.setInvitedAt(Timestamp.from(Instant.now()));
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
            .ifPresent(portalAccess -> portalAccessService.deleteById(portalAccess.getId()));

        appAccessRepository
            .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
            .stream()
            .map(AppAccess::getId)
            .forEach(appAccessService::deleteById);

        userRepository
            .findById(userId)
            .ifPresent(user -> {
                user.setDeleteOperation(true);
                user.setDeletedAt(Timestamp.from(Instant.now()));
                userRepository.saveAndFlush(user);
            });
    }

    @Transactional
    public void undelete(UUID id) {
        User entity = userRepository.findById(id).orElseThrow(() -> new NotFoundException("User: " + id));
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

    @Transactional
    public Set<TermsAndConditions> getAllLatestTermsAndConditions() {
        return Arrays.stream(TermsAndConditionsType.values())
            .map(type -> termsAndConditionsRepository.findFirstByTypeOrderByCreatedAtDesc(type)
                .orElse(null))
            .collect(Collectors.toSet());
    }
}
