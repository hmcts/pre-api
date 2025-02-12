package uk.gov.hmcts.reform.preapi.tasks;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class AddNROUserIT extends IntegrationTestBase {

    @Autowired
    private AppAccessRepository appAccessRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private static UserAuthenticationService userAuthenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    private static final String testUsersFile =
        "src/integrationTest/java/uk/gov/hmcts/reform/preapi/utils/Test_NRO_User_Import.csv";
    private static final String CRON_USER_EMAIL = "Phoebe.Revolta@HMCTS.net";


    @BeforeEach
    void setUp() {
        this.populateRolesTable();
        this.createFooCourt();
    }

    // test NRO users are imported successfully
    @DisplayName("Test NRO users listed in a CSV file are successfully imported to an ImportedNROUser object")
    @Transactional
    @Test
    public void testRunAddNROUsers() {
        // NRO objects to test assertions:
        ImportedNROUser[] testImportedNROUsers = getTestImportedNROUsers();

        // initialise & run the AddNROUsers test
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  userRepository,
                                                  testUsersFile);
        addNROUsers.run();

        for (ImportedNROUser testImportedNROUser : testImportedNROUsers) {
            // test NRO user without a court is NOT created successfully (NOT in DB)
            if (testImportedNROUser.getCourt().equals("Foo Court D")) {
                assertTrue(userRepository
                               .findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail()).isEmpty());
            } else {
                // test NRO user is created successfully
                assertEquals(testImportedNROUser.getEmail(),
                             userService.findByEmail(testImportedNROUser.getEmail()).getUser().getEmail());
            }
        }

        // now check users are obscured in the DB
        Map<String, UUID> testUserEmailsAndIDs = new HashMap<>();
        for (ImportedNROUser testImportedNROUser : testImportedNROUsers) {
            if (!(testImportedNROUser.getCourt().equals("Foo Court D"))
                && (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail())
                .isPresent())) {
                testUserEmailsAndIDs.put(testImportedNROUser.getEmail(),
                                   userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(
                                       testImportedNROUser.getEmail()).get().getId());
            }
        }

        // initialise & run the ObscureNROUsers test
        ObscureNROUsers obscureNROUsers = new ObscureNROUsers(userService,
                                                              userAuthenticationService,
                                                              CRON_USER_EMAIL,
                                                              appAccessRepository,
                                                              courtRepository,
                                                              roleRepository,
                                                              testUsersFile);
        obscureNROUsers.run();

        log.info("Checking user deletion from DB is successful. . .");
        for (Map.Entry<String,UUID> entry : testUserEmailsAndIDs.entrySet()) {
            // check current user email does not exist
            assertTrue(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(entry.getKey()).isEmpty());
            // check current user still does exist (and that the data has just been obscured)
            assertTrue(userRepository.findById(entry.getValue()).isPresent());
            // check that the email has been obscured to be the id & @test.com
            assertEquals(entry.getValue().toString() + "@test.com", userRepository.findById(
                entry.getValue()).get().getEmail());
            // check the user's first and last name are Example User respectively
            assertEquals("Example", userRepository.findById(entry.getValue()).get().getFirstName());
            assertEquals("User", userRepository.findById(entry.getValue()).get().getLastName());
            // check the user's app access entry(ies) still exist (and that the data has just been obscured)
            assertTrue(appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                entry.getValue()).isPresent());

            for (AppAccess appAccess : appAccessRepository
                .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(entry.getValue()).get()) {
                // check that the current app access entry has the same user id as the current user being tested
                assertEquals(entry.getValue(), appAccess.getUser().getId());
                // no test for default court; doesn't matter
                // check that the current app access court has been obscured to Foo Court
                assertEquals("Foo Court", appAccess.getCourt().getName());
                // check that the current app access has been given the lowest access level (Level 1)
                assertEquals("Level 1", appAccess.getRole().getName());
                // check that the current app access isActive has been set to False
                assertFalse(appAccess.isActive());
            }
        }
        // checking all emails are in the SQL statement to erase the audit logs
    }

    @Transactional
    @Test
    public void testSetters() {
        ImportedNROUser[] testImportedNROUsers = getTestImportedNROUsers();
        testImportedNROUsers[0].setFirstName("Updated");
        testImportedNROUsers[0].setLastName("Example-User A");
        testImportedNROUsers[0].setEmail("updatedUserA@test.com");
        testImportedNROUsers[0].setIsDefault(false);
        testImportedNROUsers[0].setCourt("Updated Court Name");
        testImportedNROUsers[0].setUserAccess("1");

        assertEquals("Updated", testImportedNROUsers[0].getFirstName());
        assertEquals("Example-User A", testImportedNROUsers[0].getLastName());
        assertEquals("updatedUserA@test.com", testImportedNROUsers[0].getEmail());
        assertFalse(testImportedNROUsers[0].getIsDefault());
        assertEquals("Updated Court Name", testImportedNROUsers[0].getCourt());
        assertEquals("1", testImportedNROUsers[0].getUserAccess());
    }

    private static ImportedNROUser @NotNull [] getTestImportedNROUsers() {
        ImportedNROUser testImportedNROUserA = new ImportedNROUser("Example",
                                                                   "User A",
                                                                   "exampleUserA@test.com",
                                                                   "Aylesbury Crown Court",
                                                                   true,
                                                                   "2");
        ImportedNROUser testImportedNROUserB = new ImportedNROUser("Example",
                                                                   "User B",
                                                                   "exampleUserB@test.com",
                                                                   "Basildon Combined Court",
                                                                   true,
                                                                   "2");
        ImportedNROUser testImportedNROUserBSecondaryCourt1 = new ImportedNROUser("Example",
                                                                   "User B",
                                                                   "exampleUserB@test.com",
                                                                   "Bolton Combined Court",
                                                                   false,
                                                                   "2");
        ImportedNROUser testImportedNROUserC = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Caernarfon Justice Centre",
                                                                   true,
                                                                   "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt1 = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Cambridge Crown Court",
                                                                   false,
                                                                   "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt2 = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Canterbury Combined Court",
                                                                   false,
                                                                   "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt3 = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Cardiff Crown Court",
                                                                   false,
                                                                   "2");
        // User whose court does not exist in the DB
        ImportedNROUser testImportedNROUserD = new ImportedNROUser("Example",
                                                                   "User D",
                                                                   "exampleUserD@test.com",
                                                                   "Foo Court D",
                                                                   true,
                                                                   "2");
        ImportedNROUser testImportedNROUserE = new ImportedNROUser("Example",
                                                                   "User E",
                                                                   "exampleUserE@test.com",
                                                                   "Doncaster Crown Court "
                                                                       + "(Doncaster Justice Centre South)",
                                                                   true,
                                                                   "2");

        return new ImportedNROUser[]{
            testImportedNROUserA,
            testImportedNROUserB,
            testImportedNROUserBSecondaryCourt1,
            testImportedNROUserC,
            testImportedNROUserCSecondaryCourt1,
            testImportedNROUserCSecondaryCourt2,
            testImportedNROUserCSecondaryCourt3,
            testImportedNROUserD,
            testImportedNROUserE
        };
    }

    @Transactional
    private void populateRolesTable() {
        var roleLvl1 = HelperFactory.createRole("Level 1");
        roleLvl1.setDescription("test");
        roleLvl1.setId(UUID.randomUUID());
        entityManager.persist(roleLvl1);
        var roleLvl2 = HelperFactory.createRole("Level 2");
        roleLvl2.setDescription("test");
        roleLvl2.setId(UUID.randomUUID());
        entityManager.persist(roleLvl2);
        var roleLvl3 = HelperFactory.createRole("Level 3");
        roleLvl3.setDescription("test");
        roleLvl3.setId(UUID.randomUUID());
        entityManager.persist(roleLvl3);
        var roleLvl4 = HelperFactory.createRole("Level 4");
        roleLvl4.setDescription("test");
        roleLvl4.setId(UUID.randomUUID());
        entityManager.persist(roleLvl4);
        var roleLvlSuper = HelperFactory.createRole("Super User");
        roleLvlSuper.setDescription("test");
        roleLvlSuper.setId(UUID.randomUUID());
        entityManager.persist(roleLvlSuper);
    }

    @Transactional
    private void createFooCourt() {
        entityManager.persist(HelperFactory.createCourt(CourtType.CROWN, "Foo Court", null));
    }
}
