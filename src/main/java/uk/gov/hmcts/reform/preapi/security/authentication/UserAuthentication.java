package uk.gov.hmcts.reform.preapi.security.authentication;

import lombok.Getter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;

import java.util.Collection;
import java.util.UUID;

@Getter
public class UserAuthentication extends AbstractAuthenticationToken {
    private final UUID userId;
    private final String email;
    private final AppAccess appAccess;

    public UserAuthentication(AppAccess access, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        userId = access.getUser().getId();
        email = access.getUser().getEmail();
        appAccess = access;
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
