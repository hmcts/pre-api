package uk.gov.hmcts.reform.preapi.security.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.ShareBookingRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserAuthenticationService {

    private final AppAccessRepository appAccessRepository;
    private final ShareBookingRepository shareBookingRepository;

    @Autowired
    public UserAuthenticationService(AppAccessRepository appAccessRepository,
                                     ShareBookingRepository shareBookingRepository) {
        this.appAccessRepository = appAccessRepository;
        this.shareBookingRepository = shareBookingRepository;
    }

    public UserAuthentication loadAppUserById(String id) {
        return validateUser(id)
            .orElseThrow(() -> new BadCredentialsException("Unauthorised user: " + id));
    }

    private Optional<UserAuthentication> validateUser(String accessId) {
        if (accessId == null || accessId.isEmpty()) {
            return Optional.empty();
        }

        UUID id;
        try {
            id = UUID.fromString(accessId);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }

        return appAccessRepository
            .findByIdAndDeletedAtNullAndUser_DeletedAtNull(id)
            .map(a -> new UserAuthentication(a, getSharedBookings(a), getAuthorities(a)));
    }

    private List<GrantedAuthority> getAuthorities(AppAccess access) {
        try {
            var role = access.getRole().getName().toUpperCase(Locale.ROOT).replace(' ', '_');
            return List.of(new SimpleGrantedAuthority("ROLE_" + role));
        } catch (Exception ignored) {
            return AuthorityUtils.NO_AUTHORITIES;
        }
    }

    private List<UUID> getSharedBookings(AppAccess access) {
        return shareBookingRepository
            .findAllSharesForUserByCourt(access.getUser().getId(), access.getCourt().getId())
            .stream()
            .map(share -> share.getBooking().getId())
            .collect(Collectors.toList());
    }
}
