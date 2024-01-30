package uk.gov.hmcts.reform.preapi.security;

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
    private final List<AppAccess> appAccess;

    public UserAuthentication(List<AppAccess> access, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        userId = access.getFirst().getUser().getId();
        email = access.getFirst().getUser().getEmail();
        appAccess = access;


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
