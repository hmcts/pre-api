package uk.gov.hmcts.reform.preapi.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.AccessType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static uk.gov.hmcts.reform.preapi.dto.validators.PortalAppAccessValidator.PORTAL_ROLE_NAME;

@Service
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class UserService {

    private final AppAccessService appAccessService;
    private final PortalAccessService portalAccessService;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TermsAndConditionsService termsAndConditionsService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    @Autowired
    public UserService(CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       UserRepository userRepository,
                       AppAccessService appAccessService,
                       PortalAccessService portalAccessService,
                       TermsAndConditionsService termsAndConditionsService) {
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.appAccessService = appAccessService;
        this.portalAccessService = portalAccessService;
        this.termsAndConditionsService = termsAndConditionsService;
    }

    @Transactional()
    public UserDTO findById(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
            .map(user ->
                     new UserDTO(
                         user,
                         termsAndConditionsService.getAllLatestTermsAndConditions()
                     ))
            .orElseThrow(() -> new NotFoundException("User: " + userId));
    }

    @Transactional
    public AccessDTO findByEmail(String email) {
        return userRepository.findByEmailOrAlternativeEmailIgnoreCaseAndDeletedAtIsNull(email)
            .map(access ->
                     new AccessDTO(
                         access,
                         termsAndConditionsService.getAllLatestTermsAndConditions()
                     ))
            .orElseThrow(() -> new NotFoundException("User: " + email));
    }

    @Transactional
    @PreAuthorize("!#includeDeleted or @authorisationService.canViewDeleted(authentication)")
    public Page<UserDTO> findAllBy(
        SearchUsers searchUsers,
        Pageable pageable
    ) {
        if (searchUsers.getCourtId() != null && !courtRepository.existsById(searchUsers.getCourtId())) {
            throw new NotFoundException("Court: " + searchUsers.getCourtId());
        }

        Set<TermsAndConditions> allLatestTermsAndConditions = termsAndConditionsService
            .getAllLatestTermsAndConditions();

        if (searchUsers.getRoleId() != null) {
            Role roleFromDb = roleRepository.findById(searchUsers.getRoleId())
                .orElseThrow(() -> new NotFoundException("Role: " + searchUsers.getRoleId()));

            // Is a portal user
            if (roleFromDb.getName().equals(PORTAL_ROLE_NAME) && searchUsers.getAccessType() == null) {
                return userRepository.searchAllBy(
                    searchUsers.getName(), searchUsers.getFirstName(), searchUsers.getLastName(),
                    searchUsers.getEmail(), searchUsers.getOrganisation(), searchUsers.getCourtId(),
                    null, true, false,
                    searchUsers.getIncludeDeleted(), searchUsers.getAppActive(), pageable
                ).map(user -> new UserDTO(user, allLatestTermsAndConditions));
            }
        }

        Page<User> returnedFromDB = userRepository.searchAllBy(
            searchUsers.getName(), searchUsers.getFirstName(), searchUsers.getLastName(),
            searchUsers.getEmail(), searchUsers.getOrganisation(), searchUsers.getCourtId(),
            searchUsers.getRoleId(),
            searchUsers.getAccessType() == AccessType.PORTAL,
            searchUsers.getAccessType() == AccessType.APP,
            searchUsers.getIncludeDeleted(), searchUsers.getAppActive(), pageable
        );
        return returnedFromDB.map(user -> new UserDTO(user, allLatestTermsAndConditions));
    }

    @Transactional
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
            if (!portalAccessService.exists(id)) {
                throw new NotFoundException("Portal Access: " + id);
            }
        });

        User entity = user.orElse(new User());
        entity.setId(createUserDTO.getId());
        entity.setFirstName(createUserDTO.getFirstName());
        entity.setLastName(createUserDTO.getLastName());
        entity.setEmail(createUserDTO.getEmail());
        entity.setAlternativeEmail(createUserDTO.getAlternativeEmail());
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
    public UpsertResult upsert(CreateInviteDTO createInviteDTO) {
        Optional<User> user = userRepository.findById(createInviteDTO.getUserId());
        if (user.isPresent() && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createInviteDTO.getUserId().toString());
        } else if (user.isPresent() && portalAccessService.isNotDeletedPortalUser(createInviteDTO.getUserId())) {
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

        portalAccessService.upsertPortalAccessEntity(
            createInviteDTO.getUserId(),
            userEntity,
            AccessStatus.INVITATION_SENT,
            Timestamp.from(Instant.now())
        );

        return UpsertResult.CREATED;
    }

    @Transactional
    public void deleteById(UUID userId) {
        if (!userRepository.existsByIdAndDeletedAtIsNull(userId)) {
            throw new NotFoundException("User: " + userId);
        }

        portalAccessService.deleteByUserId(userId);
        appAccessService.deleteByUserId(userId);

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

        appAccessService.undeleteByUserId(id);
        portalAccessService.undeleteByUserId(id);
    }


    @Transactional(readOnly = true)
    public Optional<User> findByOriginalEmail(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByOriginalEmailWithPortalAccess(String email) {
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNullWithPortalAccess(email);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByAlternativeEmail(String alternativeEmail) {
        return userRepository.findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(alternativeEmail);
    }

    @Transactional
    public UpsertResult updateAlternativeEmail(UUID userId, String alternativeEmail) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
            .orElseThrow(() -> new NotFoundException("User: " + userId));

        String trimmedEmail = alternativeEmail != null ? alternativeEmail.trim() : null;

        if (trimmedEmail != null && !trimmedEmail.isEmpty()) {

            if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
                throw new IllegalArgumentException(
                    "Alternative email format is invalid: must be a well-formed email address");
            }

            if (trimmedEmail.equalsIgnoreCase(user.getEmail())) {
                throw new IllegalArgumentException(
                    "Alternative email cannot be the same as the main email");
            }

            Optional<User> existingUser = userRepository
                .findByAlternativeEmailIgnoreCaseAndDeletedAtIsNull(trimmedEmail);
            if (existingUser.isPresent() && !existingUser.get().getId().equals(userId)) {
                throw new ConflictException(
                    "Alternative email: " + trimmedEmail + " already exists");
            }
        }

        user.setAlternativeEmail(trimmedEmail != null && !trimmedEmail.isEmpty() ? trimmedEmail : null);
        userRepository.saveAndFlush(user);

        return UpsertResult.UPDATED;
    }

    @Transactional(readOnly = true)
    public Role getRoleById(UUID roleId) {
        return roleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Role: " + roleId));
    }

    @Transactional(readOnly = true)
    public Page<UserDTO> findPortalUsersWithCjsmEmail(Pageable pageable) {
        return userRepository.findPortalUsersWithCjsmEmail(pageable).map(user -> new UserDTO(user, null));
    }
}
