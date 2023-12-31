package uk.gov.hmcts.reform.preapi.services;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PortalAccessRepository portalAccessRepository;

    @Autowired
    public UserService(AppAccessRepository appAccessRepository,
                       CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       UserRepository userRepository,
                       PortalAccessRepository portalAccessRepository) {
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
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
    @SuppressWarnings("PMD.UseObjectForClearerAPI")
    public List<UserDTO> findAllBy(
        String firstName,
        String lastName,
        String email,
        String organisation,
        UUID court,
        UUID role
    ) {
        if (court != null && !courtRepository.existsById(court)) {
            throw new NotFoundException("Court: " + court);
        }

        if (role != null && !roleRepository.existsById(role)) {
            throw new NotFoundException("Role: " + role);
        }

        return appAccessRepository.searchAllBy(firstName, lastName, email, organisation, court, role)
            .stream()
            .map(UserDTO::new)
            .collect(Collectors.toList());
    }

    @Transactional
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public UpsertResult upsert(CreateUserDTO createUserDTO) {
        var user = userRepository.findById(createUserDTO.getId());
        if (user.isPresent() && user.get().isDeleted()) {
            throw new ResourceInDeletedStateException("UserDTO", createUserDTO.getId().toString());
        }

        var court =
            createUserDTO.getCourtId() != null
                ? courtRepository.findById(createUserDTO.getCourtId())
                : Optional.empty();

        var isUpdate = user.isPresent();

        if (!isUpdate && court.isEmpty()
            || createUserDTO.getCourtId() != null && court.isEmpty()
        ) {
            throw new NotFoundException("Court: " + createUserDTO.getCourtId());
        }

        var role =
            createUserDTO.getRoleId() != null
                ? roleRepository.findById(createUserDTO.getRoleId())
                : Optional.empty();

        if (!isUpdate && role.isEmpty()
            || createUserDTO.getRoleId() != null && role.isEmpty()
        ) {
            throw new NotFoundException("Role: " + createUserDTO.getRoleId());
        }

        var userEntity = user.orElse(new User());

        userEntity.setId(createUserDTO.getId());
        userEntity.setFirstName(createUserDTO.getFirstName());
        userEntity.setLastName(createUserDTO.getLastName());
        userEntity.setEmail(createUserDTO.getEmail());
        userEntity.setOrganisation(createUserDTO.getOrganisation());
        userEntity.setPhone(createUserDTO.getPhoneNumber());
        userRepository.save(userEntity);

        var appAccessEntity = appAccessRepository
            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(createUserDTO.getId())
            .orElse(new AppAccess());

        appAccessEntity.setUser(userEntity);
        court.ifPresent(o -> appAccessEntity.setCourt((Court) o));
        role.ifPresent(o -> appAccessEntity.setRole((Role) o));
        appAccessRepository.save(appAccessEntity);

        return isUpdate ? UpsertResult.UPDATED : UpsertResult.CREATED;
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
