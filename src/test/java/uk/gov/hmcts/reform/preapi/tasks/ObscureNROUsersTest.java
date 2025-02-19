package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.PortalAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.repositories.TermsAndConditionsRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.AppAccessService;
import uk.gov.hmcts.reform.preapi.services.PortalAccessService;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ObscureNROUsers.class)
class ObscureNROUsersTest {

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

    private static final String TEST_USERS_FILE =
        "src/integrationTest/resources/Test_NRO_User_Import.csv";
    private static final String CRON_USER_EMAIL = "test@test.com";

    @DisplayName("Successfully print obscuring queries for values from test file")
    @Test
    void obscureNROUsersSuccessfully() {
        Role testRoleLvl4 = HelperFactory.createRole("Level 4");
        testRoleLvl4.setDescription("test");
        testRoleLvl4.setId(UUID.randomUUID());
        Role testRoleLvl2 = HelperFactory.createRole("Level 2");
        testRoleLvl2.setDescription("test");
        testRoleLvl2.setId(UUID.randomUUID());

        List<ImportedNROUser> testImportedNROUsers = getTestImportedNROUsers(testRoleLvl2.getId());

        when(this.roleRepository.findFirstByName("Level 2")).thenReturn(Optional.of(testRoleLvl2));

        for (ImportedNROUser testImportedNROUser : testImportedNROUsers) {
            Court testCourt = HelperFactory.createCourt(CourtType.CROWN, testImportedNROUser.getCourt(),
                                                        null);
            testImportedNROUser.setCourt(testCourt.getName());
            testImportedNROUser.setCourtID(testCourt.getId());

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
                                                  TEST_USERS_FILE);
        addNROUsers.run();

        Court obscuringTestCourt = HelperFactory.createCourt(CourtType.CROWN, "Foo Court",
                                                            null);
        when(this.courtRepository.findFirstByName("Foo Court"))
            .thenReturn(Optional.of(obscuringTestCourt));

        when(this.roleRepository.findFirstByName("Level 4")).thenReturn(Optional.of(testRoleLvl4));

        for (ImportedNROUser importedNROUser : testImportedNROUsers) {

            var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
            when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());

            var mockBaseUser = new BaseUserDTO();
            mockBaseUser.setId(UUID.randomUUID());
            mockBaseUser.setFirstName(importedNROUser.getFirstName());
            mockBaseUser.setEmail(importedNROUser.getEmail());

            var mockUser = new UserDTO();
            mockUser.setId(UUID.randomUUID());
            mockUser.setFirstName(importedNROUser.getFirstName());
            mockUser.setEmail(importedNROUser.getEmail());

            var accessDTO = mock(AccessDTO.class);

            when(accessDTO.getUser()).thenReturn(mockBaseUser);
            when(this.userService.findByEmail(importedNROUser.getEmail())).thenReturn(accessDTO);
            when(accessDTO.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));

            var userAuth = mock(UserAuthentication.class);
            when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));

        }

        ObscureNROUsers obscureNROUsers = new ObscureNROUsers(userService,
                                                  userAuthenticationService,
                                                  CRON_USER_EMAIL,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE);
        obscureNROUsers.run();

        // there should only be 5 viable NRO users to upsert into the DB (5 emails with valid rows in the csv file)
        verify(userService, times(5)).upsert((CreateUserDTO) any());

        verify(roleRepository, times(39)).findFirstByName(any());
        verify(courtRepository, times(31)).findFirstByName(any());
    }

    private List<ImportedNROUser> getTestImportedNROUsers(UUID testLvl2ID) {
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
        ImportedNROUser testImportedNROUserH = new ImportedNROUser("Example",
                                                                   "User H",
                                                                   "exampleUserH@test.com",
                                                                   "Harrow Crown Court",
                                                                   null,
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
        testUsers.add(testImportedNROUserH);

        return testUsers;
    }
}
