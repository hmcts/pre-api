package uk.gov.hmcts.reform.preapi.tasks;

import com.microsoft.graph.models.ObjectIdentity;
import com.microsoft.graph.models.User;

import java.util.List;
import java.util.Optional;

public interface B2CGraphService {

    Optional<User> findUserByPrimaryEmail(String primaryEmail);

    void updateUserIdentities(String userId, List<ObjectIdentity> identities);
}

