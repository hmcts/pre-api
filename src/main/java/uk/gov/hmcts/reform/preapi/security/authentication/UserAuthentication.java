package uk.gov.hmcts.reform.preapi.security.authentication;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
public class UserAuthentication extends AbstractAuthenticationToken {
    private final UUID userId;
    private final String email;
    private final AppAccess appAccess;
    private final UUID courtId;
    private final boolean admin;
    private final boolean portalUser;
    private final boolean appUser;
    private final List<UUID> sharedBookings;

    public UserAuthentication(
        AppAccess access,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        userId = access.getUser().getId();
        email = access.getUser().getEmail();
        appAccess = access;
        courtId = access.getCourt().getId();
        this.sharedBookings = List.of();
        appUser = true;
        portalUser = false;
        admin = authorities.stream().anyMatch(a ->
                                                  a.getAuthority().equals("ROLE_SUPER_USER")
                                                      || a.getAuthority().equals("ROLE_LEVEL_1"));
        setAuthenticated(true);
    }

    // TODO constructor for portal access request

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return null;
    }
}
