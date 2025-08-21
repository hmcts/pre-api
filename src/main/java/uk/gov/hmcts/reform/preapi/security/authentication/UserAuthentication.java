package uk.gov.hmcts.reform.preapi.security.authentication;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.PortalAccess;

import java.io.Serial;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserAuthentication extends AbstractAuthenticationToken {
    @Serial
    private static final long serialVersionUID = 1L;

    private final UUID userId;
    private final String email;
    private final AppAccess appAccess;
    private final PortalAccess portalAccess;
    private final UUID courtId;
    private final boolean admin;
    private final boolean portalUser;
    private final boolean appUser;
    private final List<UUID> sharedBookings;

    private static final String ROLE_SUPER_USER = "ROLE_SUPER_USER";
    private static final String ROLE_LEVEL_1 = "ROLE_LEVEL_1";

    public UserAuthentication(
        AppAccess access,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        userId = access.getUser().getId();
        email = access.getUser().getEmail();
        appAccess = access;
        portalAccess = null;
        courtId = access.getCourt().getId();
        sharedBookings = List.of();
        appUser = true;
        portalUser = false;
        admin = authorities.stream().anyMatch(a ->
                                                  a.getAuthority().equals(ROLE_SUPER_USER)
                                                      || a.getAuthority().equals(ROLE_LEVEL_1));
        super.setAuthenticated(true);
    }

    public UserAuthentication(
        PortalAccess access,
        List<UUID> sharedBookings,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        userId = access.getUser().getId();
        email = access.getUser().getEmail();
        appAccess = null;
        portalAccess = access;
        courtId = null;
        this.sharedBookings = sharedBookings;
        appUser = false;
        portalUser = true;
        admin = false;
        super.setAuthenticated(true);
    }

    public boolean hasRole(String role) {
        return getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(role));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}
