package uk.gov.hmcts.reform.preapi.tasks;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
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

@SpringBootTest(classes = AddNROUsers.class)
class AddNROUsersTest {

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private AppAccessRepository appAccessRepository;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private CourtRepository courtRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PortalAccessRepository portalAccessRepository;

    @MockitoBean
    private AppAccessService appAccessService;

    @MockitoBean
    private PortalAccessService portalAccessService;

    @MockitoBean
    private TermsAndConditionsRepository termsAndConditionsRepository;

    @MockitoBean
    private UserService userService;

    @Value("${cron-user-email}")
    private String cronUserEmail;
    private static final String TEST_USERS_FILE =
        "src/integrationTest/resources/Test_NRO_User_Import.csv";

    private List<ImportedNROUser> testImportedNROUsers;

    @BeforeEach
    void beforeEach() {
        var accessDto = mock(AccessDTO.class);
        var baseAppAccessDTO = mock(BaseAppAccessDTO.class);
        when(baseAppAccessDTO.getId()).thenReturn(UUID.randomUUID());

        when(userService.findByEmail(cronUserEmail)).thenReturn(accessDto);
        when(accessDto.getAppAccess()).thenReturn(Set.of(baseAppAccessDTO));

        var userAuth = mock(UserAuthentication.class);
        when(userAuthenticationService.validateUser(any())).thenReturn(Optional.ofNullable(userAuth));

        Role testRoleLvl2 = HelperFactory.createRole("Level 2");
        testRoleLvl2.setDescription("test");
        testRoleLvl2.setId(UUID.randomUUID());

        this.testImportedNROUsers = this.getTestImportedNROUsers(testRoleLvl2.getId());

        when(this.roleRepository.findFirstByName("Level 2")).thenReturn(Optional.of(testRoleLvl2));

        // return courts which do exist but otherwise have failure cases (incorrect role or primary/secondary status)
        Court uncalledTestCourt1 = HelperFactory.createCourt(CourtType.CROWN, "Gloucester Crown Court",
                                                             null);
        when(this.courtRepository.findFirstByName("Gloucester Crown Court"))
            .thenReturn(Optional.of(uncalledTestCourt1));
        Court uncalledTestCourt2 = HelperFactory.createCourt(CourtType.CROWN, "Derby Combined Court",
                                                             null);
        when(this.courtRepository.findFirstByName("Derby Combined Court"))
            .thenReturn(Optional.of(uncalledTestCourt2));

        for (ImportedNROUser testImportedNROUser : this.testImportedNROUsers) {
            Court testCourt = HelperFactory.createCourt(CourtType.CROWN, testImportedNROUser.getCourt(),
                                                        null);
            testCourt.setId(UUID.randomUUID());
            testImportedNROUser.setCourt(testCourt.getName());
            testImportedNROUser.setCourtID(testCourt.getId());

            when(this.courtRepository.findFirstByName(testImportedNROUser.getCourt()))
                .thenReturn(Optional.of(testCourt));
        }
    }

    @DisplayName("Successfully add users from test file to DB")
    @Test
    void addNROUsersSuccessfully() {
        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  cronUserEmail,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE);
        addNROUsers.run();

        verify(userService, times(4)).upsert(any(CreateUserDTO.class));

        verify(roleRepository, times(38)).findFirstByName(any());
        verify(courtRepository, times(38)).findFirstByName(any());
    }

    @DisplayName("Successfully handle exceptions for upsert failures")
    @Test
    void addNROUsersFailure() {
        when(this.userService.upsert(any(CreateUserDTO.class))).thenThrow(NotFoundException.class);

        AddNROUsers addNROUsers = new AddNROUsers(userService,
                                                  userAuthenticationService,
                                                  cronUserEmail,
                                                  courtRepository,
                                                  roleRepository,
                                                  TEST_USERS_FILE);
        addNROUsers.run();

        verify(userService, times(4)).upsert(any(CreateUserDTO.class));
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
                                                                                  "Bolton Crown Court",
                                                                                  null,
                                                                                  false,
                                                                                  testLvl2ID,
                                                                                  "2");
        ImportedNROUser testImportedNROUserC = new ImportedNROUser("Example",
                                                                   "User C",
                                                                   "exampleUserC@test.com",
                                                                   "Chelmsford Crown Court",
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
                                                                   "Hereford Crown Court",
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
