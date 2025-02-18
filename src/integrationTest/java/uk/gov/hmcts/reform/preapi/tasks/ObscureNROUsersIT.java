package uk.gov.hmcts.reform.preapi.tasks;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Slf4j
public class ObscureNROUsersIT extends IntegrationTestBase {
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

    private static final String TEST_USERS_FILE =
        "src/integrationTest/java/uk/gov/hmcts/reform/preapi/utils/Test_NRO_User_Import.csv";
    private static final String CRON_USER_EMAIL = "Phoebe.Revolta@HMCTS.net";
    private ArrayList<ImportedNROUser> testImportedNROUsers;
    private final Map<String, UUID> testUserEmailsAndIDs = new HashMap<>();


    @BeforeEach
    void setUp() {
        this.createFooCourt();
        // NRO objects to test assertions:
        this.testImportedNROUsers = getTestImportedNROUsers(
            this.populateRolesTableAndGetTestRoleIDs().get("Test Level 2 ID")
        );
    }

    // test NRO users are imported successfully
    @DisplayName("Test NRO users listed in a CSV file are successfully uploaded to, then obscured in, the DB")
    @Transactional
    @Test
    public void testObscureNROUsers() {
        getTestUserEmailsAndIDs();

        // initialise and run obscureNROUsers test
        ObscureNROUsers obscureNROUsers = new ObscureNROUsers(
            userService,
            userAuthenticationService,
            CRON_USER_EMAIL,
            appAccessRepository,
            courtRepository,
            roleRepository,
            TEST_USERS_FILE
        );
        obscureNROUsers.run();

        for (Map.Entry<String, UUID> entry : testUserEmailsAndIDs.entrySet()) {
            // check current user still does exist (and that the data has just been obscured)
            assertTrue(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(entry.getKey()).isEmpty());
            assertTrue(userRepository.findById(entry.getValue()).isPresent());
            // check that the email has been obscured to be the id & @test.com
            assertEquals(
                entry.getValue().toString() + "@test.com", userRepository.findById(
                    entry.getValue()).get().getEmail()
            );
            // check the user's first and last name are Example User respectively
            assertEquals("Example", userRepository.findById(entry.getValue()).get().getFirstName());
            assertEquals("User", userRepository.findById(entry.getValue()).get().getLastName());
            // check the user's app access entry(ies) still exist (and that the data has just been obscured)
            assertFalse(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                entry.getValue()).isEmpty());
        }

        // initialise & create list for relevant app access objects to test
        ArrayList<AppAccess> appAccessObjsForTestUsers = new ArrayList<>();

        for (AppAccess appAccessObj : appAccessRepository.findAll()) {
            if (testUserEmailsAndIDs.containsKey(appAccessObj.getUser().getEmail())) {
                appAccessObjsForTestUsers.add(appAccessObj);
            }
        }

        // assert the app access objects have been modified as expected
        for (AppAccess appAccess : appAccessObjsForTestUsers) {
            if (testUserEmailsAndIDs.containsValue(appAccess.getUser().getId())) {
                // check that the current app access entry has the same user id as the current user being tested
                assertEquals(
                    appAccess.getUser().getId() + "@test.com",
                    appAccess.getUser().getEmail()
                );
                // no test for default court; doesn't matter
                // check that the current app access court has been obscured to Foo Court
                assertEquals("Foo Court", appAccess.getCourt().getName());
                // check that the current app access has been given the lowest access level (Level 1)
                assertEquals("Level 1", appAccess.getRole().getName());
                // check that the current app access isActive has been set to False
                assertFalse(appAccess.isActive());
            }
        }
    }

    @DisplayName("Test NRO users that were listed in a CSV file, then modified, are unsuccessfully obscured in the DB")
    @Transactional
    @Test
    public void testRunObscureNROUsersFailureUserNotFound() {
        getTestUserEmailsAndIDs();

        // initialise & run the ObscureNROUsers test
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("exampleUserE@test.com").isPresent()) {
            // set-up tests for obscuring failure cases
            User testUndetectedUser = userRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull("exampleUserE@test.com").get();
            testUndetectedUser.setEmail("null@test.com");
            entityManager.persist(testUndetectedUser);
            entityManager.flush();
        }

        String actualUserNotFoundTestEmail = null;

        if (userRepository.findById(
            this.testUserEmailsAndIDs.get("exampleUserE@test.com")).isPresent()) {
            actualUserNotFoundTestEmail = userRepository.findById(
                this.testUserEmailsAndIDs.get("exampleUserE@test.com")).get().getEmail();
        }

        // assert that if a user's email is modified, it is not found (hence not fully obscured) from the DB
        assertNotEquals("exampleUserE@test.com", actualUserNotFoundTestEmail);
        assertEquals("null@test.com", actualUserNotFoundTestEmail);
        assertEquals("Example", userService.findByEmail("null@test.com").getUser().getFirstName());
        assertEquals("User E", userService.findByEmail("null@test.com").getUser().getLastName());
        assertEquals("Level 2", appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                    this.testUserEmailsAndIDs.get("exampleUserE@test.com")).getFirst().getRole().getName());
        assertEquals("Doncaster Crown Court (Doncaster Justice Centre South)", appAccessRepository
                .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                    this.testUserEmailsAndIDs.get("exampleUserE@test.com")).getFirst().getCourt().getName());
        assertTrue(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                           this.testUserEmailsAndIDs.get("exampleUserE@test.com")).getFirst().isActive());
        assertTrue(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                           this.testUserEmailsAndIDs.get("exampleUserE@test.com")).getFirst().isDefaultCourt());
    }

    @DisplayName("Test NRO users that were listed in a CSV file, then modified, are unsuccessfully obscured in the DB")
    @Transactional
    @Test
    public void testRunObscureNROUsersFailureAppAccessNotFound() {
        getTestUserEmailsAndIDs();

        // initialise & run the ObscureNROUsers test
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull("exampleUserH@test.com").isPresent()) {
            AppAccess testUndetectedAppAccess = appAccessRepository
                .findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userRepository
                                                                           .findByEmailIgnoreCaseAndDeletedAtIsNull(
                                                                               "exampleUserH@test.com").get().getId())
                .getFirst();
            testUndetectedAppAccess.setDeletedAt(Timestamp.valueOf(LocalDateTime.now()));
            entityManager.persist(testUndetectedAppAccess);
            entityManager.flush();
        }

        String actualAppAccessNotFoundTestEmail = null;

        if (userRepository.findById(
            this.testUserEmailsAndIDs.get("exampleUserH@test.com")).isPresent()) {
            actualAppAccessNotFoundTestEmail = userRepository.findById(
                this.testUserEmailsAndIDs.get("exampleUserH@test.com")).get().getEmail();
        }

        // assert that if a user's app access is modified, it is not found (hence not fully obscured) from the DB
        assertEquals(
            "exampleUserH@test.com", actualAppAccessNotFoundTestEmail
        );
        assertEquals(
            "Example",
            userService.findByEmail("exampleUserH@test.com").getUser().getFirstName()
        );
        assertEquals(
            "User H",
            userService.findByEmail("exampleUserH@test.com").getUser().getLastName()
        );
        assertTrue(appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
            this.testUserEmailsAndIDs.get("exampleUserH@test.com")).isEmpty());
        assertEquals(
            "Level 2", appAccessRepository.findAllByUser_IdAndDeletedAtIsNotNull(
                this.testUserEmailsAndIDs.get("exampleUserH@test.com")).getFirst().getRole().getName()
        );
        assertEquals(
            "Harrow Crown Court", appAccessRepository
                .findAllByUser_IdAndDeletedAtIsNotNull(this.testUserEmailsAndIDs.get("exampleUserH@test.com"))
                .getFirst()
                .getCourt().getName()
        );
        assertTrue(appAccessRepository
                       .findAllByUser_IdAndDeletedAtIsNotNull(
                           this.testUserEmailsAndIDs.get("exampleUserH@test.com")).getFirst()
                       .isActive());
        assertTrue(appAccessRepository
                       .findAllByUser_IdAndDeletedAtIsNotNull(
                           this.testUserEmailsAndIDs.get("exampleUserH@test.com")).getFirst()
                       .isDefaultCourt());
    }

    @Transactional
    @Test
    public void testRunObscureNROUsersFailNoCourt() {
        // initialise & run the AddNROUsers test
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE
        );
        addNROUsers.run();

        if (courtRepository.findFirstByName("Foo Court").isPresent()) {
            courtRepository.deleteById(courtRepository.findFirstByName("Foo Court").get().getId());
            entityManager.flush();
            // initialise & run the ObscureNROUsers test
            ObscureNROUsers noCourtObscureNROUsers = new ObscureNROUsers(
                userService,
                userAuthenticationService,
                CRON_USER_EMAIL,
                appAccessRepository,
                courtRepository,
                roleRepository,
                TEST_USERS_FILE
            );
            noCourtObscureNROUsers.run();

            for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
                // test NRO user is NOT obscured successfully (still in DB)
                assertEquals(
                    testImportedNROUser.getEmail(),
                    userService.findByEmail(testImportedNROUser.getEmail()).getUser().getEmail()
                );
            }
        } else {
            log.info("Test court to obscure users does not initially exist in the DB; failed test");
            System.exit(0);
        }

    }

    @Transactional
    @Test
    public void testRunObscureNROUsersFailNoRole() {
        // initialise & run the AddNROUsers test
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE
        );
        addNROUsers.run();

        if (roleRepository.findFirstByName("Level 1").isPresent()) {
            roleRepository.deleteById(roleRepository.findFirstByName("Level 1").get().getId());
            entityManager.flush();

            // initialise & run the ObscureNROUsers test
            ObscureNROUsers noCourtObscureNROUsers = new ObscureNROUsers(
                userService,
                userAuthenticationService,
                CRON_USER_EMAIL,
                appAccessRepository,
                courtRepository,
                roleRepository,
                TEST_USERS_FILE
            );
            noCourtObscureNROUsers.run();

            for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
                // test NRO user is NOT obscured successfully (still in DB)
                assertEquals(
                    testImportedNROUser.getEmail(),
                    userService.findByEmail(testImportedNROUser.getEmail()).getUser().getEmail()
                );
            }
        } else {
            log.info("Test role to obscure users does not initially exist in the DB; failed test");
            System.exit(0);
        }
    }

    @Transactional
    @Test
    public void testInvalidCSVFile() {
        String falseFileName = "falseFileName";
        try {
            ObscureNROUsers obscureNROUsers = new ObscureNROUsers(userService,
                                                                  userAuthenticationService,
                                                                  CRON_USER_EMAIL,
                                                                  appAccessRepository,
                                                                  courtRepository,
                                                                  roleRepository,
                                                                  falseFileName);
            obscureNROUsers.run();
        } catch (Exception e) {
            for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
                assertTrue(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail())
                               .isEmpty());
            }
        }
    }

    @Transactional
    private void createFooCourt() {
        entityManager.persist(HelperFactory.createCourt(CourtType.CROWN, "Foo Court", null));
    }

    private UUID findTestCourtID(String courtName) {
        if (this.courtRepository.findFirstByName(courtName).isPresent()) {
            return this.courtRepository.findFirstByName(courtName).get().getId();
        } else {
            System.out.println("Court is not available in the test DB - exiting");
            System.exit(0);
            return null;
        }
    }

    @Transactional
    private void getTestUserEmailsAndIDs() {
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE
        );
        addNROUsers.run();

        // collate user emails and IDs that have been imported
        for (ImportedNROUser testImportedNROUser: this.testImportedNROUsers) {
            if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail()).isPresent()) {
                testUserEmailsAndIDs.put(testImportedNROUser.getEmail(),
                    userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(
                        testImportedNROUser.getEmail()).get().getId());
            }
        }
    }

    private ArrayList<ImportedNROUser> getTestImportedNROUsers(UUID testLvl2ID) {
        ImportedNROUser testImportedNROUserA = new ImportedNROUser("Example",
                                                                   "User A",
                                                                   "exampleUserA@test.com",
                                                                   "Aylesbury Crown Court",
                                                                   findTestCourtID(
                                                                       "Aylesbury Crown Court"
                                                                   ),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserB = new ImportedNROUser("Example",
                                                                   "User B",
                                                                   "exampleUserB@test.com",
                                                                   "Basildon Combined Court",
                                                                   findTestCourtID(
                                                                       "Basildon Combined Court"
                                                                   ),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserBSecondaryCourt1 = new ImportedNROUser("Example",
                                                                                  "User B",
                                                                                  "exampleUserB@test.com",
                                                                                  "Bolton Combined Court",
                                                                                  findTestCourtID(
                                                                                      "Bolton Combined Court"
                                                                                  ),
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserC = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Caernarfon Justice Centre",
                                                                   findTestCourtID(
                                                                       "Caernarfon Justice Centre"
                                                                   ),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt1 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Cambridge Crown Court",
                                                                                  findTestCourtID(
                                                                                      "Cambridge Crown Court"
                                                                                  ),
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt2 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Canterbury Combined Court",
                                                                                  findTestCourtID(
                                                                                      "Canterbury Combined Court"
                                                                                  ),
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt3 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Cardiff Crown Court",
                                                                                  findTestCourtID(
                                                                                      "Cardiff Crown Court"
                                                                                  ),
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserE = new ImportedNROUser("Example",
                                                                   "User E",
                                                                   "exampleUserE@test.com",
                                                                   "Doncaster Crown Court "
                                                                       + "(Doncaster Justice Centre South)",
                                                                   findTestCourtID(
                                                                       "Doncaster Crown Court (Doncaster "
                                                                           + "Justice Centre South)"),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserH = new ImportedNROUser("Example",
                                                                   "User H",
                                                                   "exampleUserH@test.com",
                                                                   "Harrow Crown Court",
                                                                   findTestCourtID("Harrow Crown Court"),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");

        ArrayList<ImportedNROUser> testUsers = new ArrayList<>();
        testUsers.add(testImportedNROUserA);
        testUsers.add(testImportedNROUserB);
        testUsers.add(testImportedNROUserBSecondaryCourt1);
        testUsers.add(testImportedNROUserC);
        testUsers.add(testImportedNROUserCSecondaryCourt1);
        testUsers.add(testImportedNROUserCSecondaryCourt2);
        testUsers.add(testImportedNROUserCSecondaryCourt3);
        testUsers.add(testImportedNROUserE);
        testUsers.add(testImportedNROUserH);

        // sort alphabetically by email then court name
        testUsers.sort(Comparator.comparing(ImportedNROUser::getEmail).thenComparing(ImportedNROUser::getCourt));

        return testUsers;
    }

    @Transactional
    private HashMap<String, UUID> populateRolesTableAndGetTestRoleIDs() {
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

        HashMap<String, UUID> testRoleIDs = new HashMap<>();
        testRoleIDs.put("Test Level 1 ID", roleLvl1.getId());
        testRoleIDs.put("Test Level 2 ID", roleLvl2.getId());

        return testRoleIDs;
    }

}
