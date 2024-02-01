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
    private final boolean superUser;
    private final List<UUID> sharedBookings;

    public UserAuthentication(
        AppAccess access,
        List<UUID> sharedBookings,
        Collection<? extends GrantedAuthority> authorities
    ) {
        super(authorities);
        userId = access.getUser().getId();
        email = access.getUser().getEmail();
        appAccess = access;
        courtId = access.getCourt().getId();
        this.sharedBookings = sharedBookings;
        superUser = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_USER"));
        setAuthenticated(true);
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
