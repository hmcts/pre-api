package uk.gov.hmcts.reform.preapi.tasks;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.entities.base.BaseEntity;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;
import uk.gov.hmcts.reform.preapi.utils.IntegrationTestBase;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
class AddNROUsersIT extends IntegrationTestBase {

    @Autowired
    private AppAccessRepository appAccessRepository;
    @Autowired
    private CourtRepository courtRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserAuthenticationService userAuthenticationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserService userService;

    @Value("${cron-user-email}")
    private String cronUserEmail;
    private static final String TEST_USERS_FILE = "src/integrationTest/resources/Test_NRO_User_Import.csv";

    private List<String> failureCaseEmails;
    private List<ImportedNROUser> testImportedNROUsers;


    @BeforeEach
    void setUp() throws Exception {
        var mockUser = HelperFactory.createUser("Example", "Admin-User", cronUserEmail,
                                                null, null, null);
        entityManager.persist(mockUser);
        var mockCourt = HelperFactory.createCourt(CourtType.CROWN, "Admin Court", null);
        entityManager.persist(mockCourt);
        var mockRole = HelperFactory.createRole("Super User");
        entityManager.persist(mockRole);
        var mockAppAccess = HelperFactory.createAppAccess(mockUser, mockCourt, mockRole, true, null,
                                                          null, true);
        entityManager.persist(mockAppAccess);
        entityManager.flush();
        entityManager.refresh(mockUser);

        // NRO objects to test assertions:
        this.testImportedNROUsers = this.getTestImportedNROUsers(
            this.populateRolesTableAndGetTestRoleIDs().get("Test Level 2 ID")
        );
        this.failureCaseEmails = List.of(
            "exampleUserD@test.com",
            "exampleUserF@test.com",
            "exampleUserG@test.com",
            "exampleUserH@test.com",
            "exampleUserI@test.com",
            "exampleUserJ@test.com",
            "exampleUserK@test.com",
            "exampleUserL@test.com",
            "exampleUserM@test.com",
            "exampleUserN@test.com",
            "exampleUserO@test.com"
        );
    }

    // test NRO users are imported successfully
    @DisplayName("Test NRO users listed in a CSV file are successfully uploaded to the DB")
    @Transactional
    @Test
    void testRunAddNROUsers() {
        // initialise & run the AddNROUsers test
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  cronUserEmail,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE
        );
        addNROUsers.run();

        // assert the nro users in the csv file which have invalid input are NOT added to the DB
        for (String failureCaseEmail : failureCaseEmails) {
            assertTrue(userRepository
                           .findByEmailIgnoreCaseAndDeletedAtIsNull(failureCaseEmail).isEmpty());
        }

        Map<String, UUID> testUserEmailsAndIDs = new HashMap<>();

        // assert the rest of the nro users are inserted into the DB with the correct values
        for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
            // test NRO user is created successfully, with at least one appAccess object
            assertTrue(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail())
                           .isPresent());
            assertEquals(testImportedNROUser.getFirstName(), userService.findByEmail(testImportedNROUser.getEmail())
                .getUser().getFirstName());
            assertEquals(testImportedNROUser.getLastName(), userService.findByEmail(testImportedNROUser.getEmail())
                .getUser().getLastName());

            if (testImportedNROUser.getEmail().contains("B")) { // assert test object B has 2 app access objects
                assertEquals(
                    2,
                    appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                        userService.findByEmail(testImportedNROUser.getEmail()).getUser().getId()).size()
                );
            } else if (testImportedNROUser.getEmail().contains("C")) { // assert test object C has 4 app access objects
                assertEquals(
                    4,
                    appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                        userService.findByEmail(testImportedNROUser.getEmail()).getUser().getId()).size()
                );
            } else { // assert all other test objects have just one app access object
                assertEquals(
                    1,
                    appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                        userService.findByEmail(testImportedNROUser.getEmail()).getUser().getId()).size()
                );
            }

            // add corresponding ID in DB for current email
            testUserEmailsAndIDs.put(testImportedNROUser.getEmail(),
                                     userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(
                                         testImportedNROUser.getEmail()).get().getId());
        }

        // initialise & create list for relevant app access objects to test
        List<AppAccess> appAccessObjsForTestUsers = appAccessRepository.findAll().stream()
            .filter(appAccessObj ->
                        testUserEmailsAndIDs.containsKey(appAccessObj.getUser().getEmail())).sorted(
                Comparator.comparing((AppAccess appAccess) -> appAccess.getUser().getEmail())
                    .thenComparing(appAccess -> appAccess.getCourt().getName())).toList();

        assertEquals(8, appAccessObjsForTestUsers.size());

        // iterate through relevant app access objects and assert each object has their expected values
        int index = 0;
        for (AppAccess appAccessObjForTestUser : appAccessObjsForTestUsers) {
            assertEquals(this.testImportedNROUsers.get(index).getEmail(), appAccessObjForTestUser.getUser().getEmail());
            assertEquals(this.testImportedNROUsers.get(index).getCourt(), appAccessObjForTestUser.getCourt().getName());
            assertEquals("Level " + this.testImportedNROUsers.get(index).getUserAccess(),
                         appAccessObjForTestUser.getRole().getName());
            assertEquals(this.testImportedNROUsers.get(index).getIsDefault(), appAccessObjForTestUser.isDefaultCourt());
            index++;
        }
    }

    @Transactional
    @Test
    public void testSettersAndToString() {
        mockAdminUser();
        UUID testNewCourtID = UUID.randomUUID();
        UUID testNewRoleID = UUID.randomUUID();

        this.testImportedNROUsers.getFirst().setFirstName("Updated");
        this.testImportedNROUsers.getFirst().setLastName("Example-User A");
        this.testImportedNROUsers.getFirst().setEmail("updatedUserA@test.com");
        this.testImportedNROUsers.getFirst().setIsDefault(false);
        this.testImportedNROUsers.getFirst().setCourt("Updated Court Name");
        this.testImportedNROUsers.getFirst().setCourtID(testNewCourtID);
        this.testImportedNROUsers.getFirst().setRoleID(testNewRoleID);
        this.testImportedNROUsers.getFirst().setUserAccess("1");

        assertEquals("Updated", this.testImportedNROUsers.getFirst().getFirstName());
        assertEquals("Example-User A", this.testImportedNROUsers.getFirst().getLastName());
        assertEquals("updatedUserA@test.com", this.testImportedNROUsers.getFirst().getEmail());
        assertFalse(this.testImportedNROUsers.getFirst().getIsDefault());
        assertEquals("Updated Court Name", this.testImportedNROUsers.getFirst().getCourt());
        assertEquals(testNewCourtID, this.testImportedNROUsers.getFirst().getCourtID());
        assertEquals(testNewRoleID, this.testImportedNROUsers.getFirst().getRoleID());
        assertEquals("1", this.testImportedNROUsers.getFirst().getUserAccess());
        assertEquals("ImportedNROUser(firstName=Updated, lastName=Example-User A, "
                        + "email=updatedUserA@test.com, court=Updated Court Name, courtID="
                         + testNewCourtID + ", isDefault=false, roleID="
                         + testNewRoleID + ", userAccess=1)",
                     this.testImportedNROUsers.getFirst().toString());
    }

    @Transactional
    @Test
    void testInvalidCSVFile() {
        mockAdminUser();
        String falseFileName = "falseFileName";
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                userAuthenticationService,
                cronUserEmail,
                courtRepository,
                roleRepository,
                falseFileName);

        assertThrows(RuntimeException.class, addNROUsers::run);

        try {
            addNROUsers.run();
        } catch (Exception e) {
            for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
                assertTrue(userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(testImportedNROUser.getEmail())
                               .isEmpty());
            }
        }
    }

    private UUID findTestCourtID(String courtName) throws Exception {
        return courtRepository.findFirstByName(courtName)
            .map(BaseEntity::getId)
            .orElseThrow(() -> new Exception("Court '" + courtName + "' is not available in the test DB"));
    }

    private List<ImportedNROUser> getTestImportedNROUsers(UUID testLvl2ID) throws Exception {
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
                                                                                  "Bolton Crown Court",
                                                                                  findTestCourtID(
                                                                                      "Bolton Crown Court"
                                                                                  ),
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserC = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Chelmsford Crown Court",
                                                                   findTestCourtID(
                                                                       "Chelmsford Crown Court"
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
                                                                   "Hereford Crown Court",
                                                                   findTestCourtID(
                                                                       "Hereford Crown Court"),
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");

        List<ImportedNROUser> testUsers = new ArrayList<>();
        testUsers.add(testImportedNROUserA);
        testUsers.add(testImportedNROUserB);
        testUsers.add(testImportedNROUserBSecondaryCourt1);
        testUsers.add(testImportedNROUserC);
        testUsers.add(testImportedNROUserCSecondaryCourt1);
        testUsers.add(testImportedNROUserCSecondaryCourt2);
        testUsers.add(testImportedNROUserCSecondaryCourt3);
        testUsers.add(testImportedNROUserE);

        // sort alphabetically by email then court name
        testUsers.sort(Comparator.comparing(ImportedNROUser::getEmail).thenComparing(ImportedNROUser::getCourt));

        return testUsers;
    }

    @Transactional
    protected HashMap<String, UUID> populateRolesTableAndGetTestRoleIDs() {
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

        HashMap<String, UUID> testRoleIDs = new HashMap<>();
        testRoleIDs.put("Test Level 1 ID", roleLvl1.getId());
        testRoleIDs.put("Test Level 2 ID", roleLvl2.getId());

        return testRoleIDs;
    }
}
