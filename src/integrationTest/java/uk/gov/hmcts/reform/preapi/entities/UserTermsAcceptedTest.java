package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
public class UserTermsAcceptedTest extends IntegrationTestBase {

    @Test
    @Transactional
    public void testSaveAndRetrieve() {
        var user = HelperFactory.createDefaultTestUser();
        entityManager.persist(user);
        entityManager.flush();

        var termsAndConditions = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "some content");
        entityManager.persist(termsAndConditions);
        entityManager.flush();

        var termsAccepted = HelperFactory.createUserTermsAccepted(
            user,
            termsAndConditions,
            Timestamp.from(Instant.now())
        );
        entityManager.persist(termsAccepted);
        entityManager.flush();
        entityManager.refresh(user);

        var retrieved = entityManager.find(UserTermsAccepted.class, termsAccepted.getId());
        assertThat(retrieved).isNotNull();
        assertThat(retrieved.getId()).isEqualTo(termsAccepted.getId());
        assertThat(retrieved.getUser()).isNotNull();
        assertThat(retrieved.getUser().getId()).isEqualTo(user.getId());
        assertThat(retrieved.getTermsAndConditions()).isNotNull();
        assertThat(retrieved.getTermsAndConditions().getId()).isEqualTo(termsAndConditions.getId());
        assertThat(retrieved.getAcceptedAt()).isEqualTo(termsAccepted.getAcceptedAt());

        var retrievedUser = entityManager.find(User.class, user.getId());
        assertThat(retrievedUser).isNotNull();
        assertThat(retrievedUser.getId()).isEqualTo(user.getId());
        assertThat(retrievedUser.getUserTermsAccepted()).isNotNull();
        assertThat(retrievedUser.getUserTermsAccepted()).hasSize(1);
        assertThat(retrievedUser.getUserTermsAccepted().stream().findFirst().get().getId())
            .isEqualTo(termsAccepted.getId());
    }
}
