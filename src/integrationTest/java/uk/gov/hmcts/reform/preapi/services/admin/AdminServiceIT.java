package uk.gov.hmcts.reform.preapi.services.admin;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.admin.AdminRepository;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class AdminServiceIT extends IntegrationTestBase {

    @Autowired
    AdminService adminService;

    @Autowired
    AdminRepository adminRepository;

    @Autowired
    protected EntityManager entityManager;

    @Test
    @Transactional
    public void shouldCheckUuidExists() {
        //Put a user in the database to test UUID against
        User user = HelperFactory.createUser("Example", "One", "example1@example.com", null, null, null);
        entityManager.persist(user);
        entityManager.flush();

        UUID generatedId = user.getId();

        AdminService.UuidTableType tableTypeReturnedFromDb = adminService.findUuidType(generatedId);
        assertThat(tableTypeReturnedFromDb).isEqualTo(AdminService.UuidTableType.USER);
    }
}
