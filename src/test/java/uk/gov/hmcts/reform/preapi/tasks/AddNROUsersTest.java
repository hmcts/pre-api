package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.AppAccessService;
import uk.gov.hmcts.reform.preapi.services.PortalAccessService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = AddNROUsers.class)
public class AddNROUsersTest {

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private AppAccessRepository appAccessRepository;

    @MockBean
    private RoleRepository roleRepository;

    @MockBean
    private CourtRepository courtRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PortalAccessRepository portalAccessRepository;

    @MockBean
    private AppAccessService appAccessService;

    @MockBean
    private PortalAccessService portalAccessService;

    @MockBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

    @MockBean
    private UserService userService;

    private static final String testUsersFile =
        "src/integrationTest/java/uk/gov/hmcts/reform/preapi/utils/Test_NRO_User_Import.csv";
    private static final String CRON_USER_EMAIL = "Phoebe.Revolta@HMCTS.net";

    @DisplayName("Successfully add users from test file to DB")
    @Test
    void addNROUsersSuccessfully() {
        Role testRoleLvl1 = HelperFactory.createRole("Level 1");
        testRoleLvl1.setDescription("test");
        testRoleLvl1.setId(UUID.randomUUID());
        Role testRoleLvl2 = HelperFactory.createRole("Level 2");
        testRoleLvl2.setDescription("test");
        testRoleLvl2.setId(UUID.randomUUID());

        ArrayList<ImportedNROUser> testImportedNROUsers = getTestImportedNROUsers(testRoleLvl2.getId());

        when(this.roleRepository.findFirstByName("Level 2")).thenReturn(Optional.of(testRoleLvl2));

        for (ImportedNROUser testImportedNROUser : testImportedNROUsers) {
            Court testCourt = HelperFactory.createCourt(CourtType.CROWN, testImportedNROUser.getCourt(),
                                                        null);
            testImportedNROUser.setCourt(testCourt.getName());
            testImportedNROUser.setCourtId(testCourt.getId());

            when(this.courtRepository.findFirstByName(testImportedNROUser.getCourt()))
                .thenReturn(Optional.of(testCourt));
        }

        // return courts which do exist but otherwise have failure cases (incorrect role or primary/secondary status)
        Court uncalledTestCourt = HelperFactory.createCourt(CourtType.CROWN, "Gloucester Crown Court",
                                                            null);
        when(this.courtRepository.findFirstByName("Gloucester Crown Court"))
            .thenReturn(Optional.of(uncalledTestCourt));

        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  testUsersFile);
        addNROUsers.run();

        // there should only be 4 viable NRO users to upsert into the DB (4 emails with valid rows in the csv file)
        verify(userService, times(4)).upsert((CreateUserDTO) any());

        // calls to court and role repos are made twice (if all fields successful) during validation
        // a failing court or a failing role will still be called once
        // neither the court nor the role will be called if there is an invalid primary/secondary status
        // out of 11 rows, if all 11 are valid, there should be 22 calls each to the court repo and the role repo resp.
        // but there should be two failing courts and two failing roles
        // two calls each will never be called due to one row with an invalid primary/secondary status
        // the role will also not be called if there is a failing court
        verify(roleRepository, times(17)).findFirstByName(any());
        verify(courtRepository, times(19)).findFirstByName(any());
    }

    @DisplayName("Successfully throw exceptions for upsert failures")
    @Test
    void addNROUsersFailure() {
        Role testRoleLvl1 = HelperFactory.createRole("Level 1");
        testRoleLvl1.setDescription("test");
        testRoleLvl1.setId(UUID.randomUUID());
        Role testRoleLvl2 = HelperFactory.createRole("Level 2");
        testRoleLvl2.setDescription("test");
        testRoleLvl2.setId(UUID.randomUUID());

        ArrayList<ImportedNROUser> testImportedNROUsers = getTestImportedNROUsers(testRoleLvl2.getId());

        when(this.roleRepository.findFirstByName("Level 2")).thenReturn(Optional.of(testRoleLvl2));

        for (ImportedNROUser testImportedNROUser : testImportedNROUsers) {
            Court testCourt = HelperFactory.createCourt(CourtType.CROWN, testImportedNROUser.getCourt(),
                                                        null);
            testImportedNROUser.setCourt(testCourt.getName());
            testImportedNROUser.setCourtId(testCourt.getId());

            when(this.courtRepository.findFirstByName(testImportedNROUser.getCourt()))
                .thenReturn(Optional.of(testCourt));
        }

        // return courts which do exist but otherwise have failure cases (incorrect role or primary/secondary status)
        Court uncalledTestCourt = HelperFactory.createCourt(CourtType.CROWN, "Gloucester Crown Court",
                                                            null);
        when(this.courtRepository.findFirstByName("Gloucester Crown Court"))
            .thenReturn(Optional.of(uncalledTestCourt));

        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  testUsersFile);
        addNROUsers.run();

        when(userService.upsert((CreateUserDTO) any())).thenThrow(NotFoundException.class);

        verify(userService, times(4)).upsert((CreateUserDTO) any());
        verify(userService, times(4)).upsert((CreateUserDTO) any());

        for (ImportedNROUser testUnimportedNROUser : testImportedNROUsers) {
            assertTrue(userRepository
                           .findByEmailIgnoreCaseAndDeletedAtIsNull(testUnimportedNROUser.getEmail()).isEmpty());
        }
    }

    private ArrayList<ImportedNROUser> getTestImportedNROUsers(UUID testLvl2ID) {
        ImportedNROUser testImportedNROUserA = new ImportedNROUser("Example",
                                                                   "User A",
                                                                   "exampleUserA@test.com",
                                                                   "Aylesbury Crown Court",
                                                                   null,
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserB = new ImportedNROUser("Example",
                                                                   "User B",
                                                                   "exampleUserB@test.com",
                                                                   "Basildon Combined Court",
                                                                   null,
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserBSecondaryCourt1 = new ImportedNROUser("Example",
                                                                                  "User B",
                                                                                  "exampleUserB@test.com",
                                                                                  "Bolton Combined Court",
                                                                                  null,
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserC = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Caernarfon Justice Centre",
                                                                   null,
                                                                   true,
                                                                   testLvl2ID,
                                                                   "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt1 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Cambridge Crown Court",
                                                                                  null,
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt2 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Canterbury Combined Court",
                                                                                  null,
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserCSecondaryCourt3 = new ImportedNROUser("Example",
                                                                                  "User C",
                                                                                  "exampleUserC@test.com",
                                                                                  "Cardiff Crown Court",
                                                                                  null,
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserE = new ImportedNROUser("Example",
                                                                   "User E",
                                                                   "exampleUserE@test.com",
                                                                   "Doncaster Crown Court "
                                                                       + "(Doncaster Justice Centre South)",
                                                                   null,
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

        return testUsers;
    }
}
