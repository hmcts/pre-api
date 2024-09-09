package uk.gov.hmcts.reform.preapi.entities;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.gov.hmcts.reform.preapi.Application;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
public class TermsAndConditionsTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @Transactional
    public void testSaveAndRetrieveTermsAndConditions() {
        var termsAndConditions = HelperFactory.createTermsAndConditions(TermsAndConditionsType.APP, "some content");
        entityManager.persist(termsAndConditions);
        entityManager.flush();

        var retreivedTermsAndConditions = entityManager.find(TermsAndConditions.class, termsAndConditions.getId());

        assertThat(retreivedTermsAndConditions).isNotNull();
        assertThat(retreivedTermsAndConditions.getId()).isEqualTo(termsAndConditions.getId());
        assertThat(retreivedTermsAndConditions.getContent()).isEqualTo(termsAndConditions.getContent());
        assertThat(retreivedTermsAndConditions.getType()).isEqualTo(termsAndConditions.getType());
    }
}
