package uk.gov.hmcts.reform.preapi.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAuthenticationService {

    private final AppAccessRepository appAccessRepository;
    private final PortalAccessRepository portalAccessRepository;
    private final ShareBookingRepository shareBookingRepository;

    @Autowired
    public UserAuthenticationService(AppAccessRepository appAccessRepository,
                                     PortalAccessRepository portalAccessRepository,
                                     ShareBookingRepository shareBookingRepository) {
        this.appAccessRepository = appAccessRepository;
        this.portalAccessRepository = portalAccessRepository;
        this.shareBookingRepository = shareBookingRepository;
    }

    @Transactional
    public UserAuthentication loadAppUserById(String id) {
        return validateUser(id)
            .orElseThrow(() -> new BadCredentialsException("Unauthorised user: " + id));
    }

    @Transactional
    public UserAuthentication getAuthentication(AppAccess access) {
        access.setLastAccess(Timestamp.from(Instant.now()));
        appAccessRepository.saveAndFlush(access);
        return new UserAuthentication(access, getAuthorities(access));
    }

    @Transactional
    public UserAuthentication getAuthentication(PortalAccess access) {
        access.setLastAccess(Timestamp.from(Instant.now()));
        portalAccessRepository.saveAndFlush(access);
        return new UserAuthentication(access, getSharedBookings(access), getAuthorities());
    }

    @Transactional
    public Optional<UserAuthentication> validateUser(String accessId) {
        if (accessId == null || accessId.isEmpty()) {
            return Optional.empty();
        }

        UUID id;
        try {
            id = UUID.fromString(accessId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        var user = appAccessRepository
            .findByIdValidUser(id)
            .map(this::getAuthentication);

        return user.isPresent()
            ? user
            : portalAccessRepository.findByIdValidUser(id)
            .map(this::getAuthentication);
    }

    private List<GrantedAuthority> getAuthorities(AppAccess access) {
        try {
            var role = access.getRole().getName().toUpperCase(Locale.ROOT).replace(' ', '_');
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        } catch (Exception ignored) {
            return AuthorityUtils.NO_AUTHORITIES;
        }
    }

    private List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_LEVEL_3"));
    }

    private List<UUID> getSharedBookings(PortalAccess access) {
        return shareBookingRepository
            .findAllBySharedWith_IdAndDeletedAtIsNull(access.getUser().getId())
            .stream()
            .map(share -> share.getBooking().getId())
            .collect(Collectors.toList());
    }
}
