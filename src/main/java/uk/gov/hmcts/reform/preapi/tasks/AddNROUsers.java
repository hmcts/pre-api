package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class AddNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final ArrayList<String> otherUsersNotImported = new ArrayList<>();
    private final ArrayList<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final ArrayList<CreateUserDTO> nroUsers = new ArrayList<>();
    private final RoleRepository roleRepository;
    private Boolean stopScript = false;
    private String usersFile = "src/integrationTest/java/uk/gov/hmcts/reform/preapi/utils/Test_NRO_User_Import.csv";
    private final ArrayList<String> usersWithoutCourts = new ArrayList<>();


    @Autowired
    public AddNROUsers(UserService userService,
                       UserAuthenticationService userAuthenticationService,
                       @Value("${cron-user-email}") String cronUserEmail, CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       @Value("${nroUsersFilePath}") String usersFile) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.usersFile = usersFile;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Running AddNROUsers task");

        log.info("Reading in .csv file. . .");
        this.createImportedNROUserObjects(this.usersFile);
        // if there were any IO errors in the .csv file, exit
        if (this.stopScript.equals(Boolean.TRUE)) {
            return;
        }

        // create a new user for each email,
        log.info("Creating new users. . .");
        this.createUsers();

        log.info("Upserting createUserDTOs to DB. . .");
        for (CreateUserDTO createUserDTOToUpsert : this.nroUsers) {
            // add user to DB (assuming they do not exist already)
            try {
                this.userService.upsert(createUserDTOToUpsert);
            } catch (Exception e) {
                // if the upserting of the current user fails, add them to a list of users which have not been uploaded
                this.otherUsersNotImported.add(createUserDTOToUpsert.getEmail());
                log.info("Upsert failed for user: {}", createUserDTOToUpsert.getEmail());
            }
        }

        log.info("Uninserted users without courts:");
        for (String importedNROUserEmail : this.usersWithoutCourts) {
            log.info(importedNROUserEmail);
        }
        log.info("Otherwise uninserted users:");
        for (String uninsertedUser : this.otherUsersNotImported) {
            log.info(uninsertedUser);
        }

        log.info("Completed AddNROUsers task");

    }

    private CreateAppAccessDTO createAppAccessObj(ImportedNROUser importedNROUser, UUID userId) {
        CreateAppAccessDTO userAppAccess = new CreateAppAccessDTO();

        // values have been validated in getNROUser
        userAppAccess.setId(UUID.randomUUID());
        userAppAccess.setUserId(userId);
        userAppAccess.setRoleId(importedNROUser.getRoleId());
        userAppAccess.setCourtId(importedNROUser.getCourtId());
        userAppAccess.setDefaultCourt(importedNROUser.getIsDefault());

        return userAppAccess;
    }

    private void createImportedNROUserObjects(String usersFilePath) {
        // Read from CSV file
        try (BufferedReader br = new BufferedReader(new FileReader(usersFilePath))) {
            String line;
            // Skip header if there is one

            // Read each line
            while ((line = br.readLine()) != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    continue;
                }
                String[] values = ImportedNROUser.parseCsvLine(line);

                ImportedNROUser importedNROUser = getNROUser(values);
                if (importedNROUser != null) {
                    this.importedNROUsers.add(importedNROUser);
                }

            }
        } catch (IOException e) {
            log.error("Error: " + e.getMessage());
            this.stopScript = true;
        }
    }

    private void createUsers() {
        // sort list of imported NRO users in alphabetical order by email (& then by court)
        this.importedNROUsers.sort(Comparator.comparing(ImportedNROUser::getEmail)
                                       .thenComparing(ImportedNROUser::getCourt));

        // initialise values
        String previousEmail = null;
        UUID currentUserId = null;
        Set<CreateAppAccessDTO> createAppAccessDTOs = null;
        CreateUserDTO createUserDTO = null;
        int index = 0;

        for (ImportedNROUser importedNROUser : this.importedNROUsers) {
            // if the previous email and current email are not the same, make a new user
            if (!Objects.equals(importedNROUser.getEmail(), previousEmail)) {
                currentUserId = UUID.randomUUID();
                createUserDTO = new CreateUserDTO();
                createUserDTO.setId(currentUserId);
                createUserDTO.setFirstName(importedNROUser.getFirstName());
                createUserDTO.setLastName(importedNROUser.getLastName());
                createUserDTO.setEmail(importedNROUser.getEmail());

                // create a (empty) set of PortalAccess objects for each user
                Set<CreatePortalAccessDTO> createPortalAccessDTOS = new HashSet<>(){};
                createUserDTO.setPortalAccess(createPortalAccessDTOS);

                // then create an AppAccess object for each primary and secondary court of the user
                createAppAccessDTOs = new HashSet<>(){};
            }

            CreateAppAccessDTO userAppAccess = this.createAppAccessObj(importedNROUser, currentUserId);
            createAppAccessDTOs.add(userAppAccess);

            // if this is the last element, or if the next element is a new email,
            if ((index == (this.importedNROUsers.size() - 1)
                || !(Objects.equals(this.importedNROUsers.get(index + 1).getEmail(), createUserDTO.getEmail())))) {
                // assign all the app access objects for this user to the current user,
                createUserDTO.setAppAccess(createAppAccessDTOs);
                // then add the user to the list of users to upload
                this.nroUsers.add(createUserDTO);
            }

            index++;
            previousEmail = importedNROUser.getEmail();
        }

        // old method:

        // group the new list of NRO users (objects) by email
        // log.info("Grouping NRO user courts by email. . .");
        // Map<String, List<ImportedNROUser>> groupedByEmail = this.importedNROUsers.stream()
        // .collect(Collectors.groupingBy(ImportedNROUser::getEmail));

        // for (Map.Entry<String, List<ImportedNROUser>> entry : groupedByEmail.entrySet()) {
        // CreateUserDTO createUserDTO = new CreateUserDTO();
        // createUserDTO.setId(UUID.randomUUID());
        // createUserDTO.setFirstName(entry.getValue().getFirst().getFirstName());
        // createUserDTO.setLastName(entry.getValue().getFirst().getLastName());
        // createUserDTO.setEmail(entry.getKey());             // entry.getKey() is the user email

        // create a (empty) set of PortalAccess objects for each user
        // Set<CreatePortalAccessDTO> createPortalAccessDTOS = new HashSet<>(){};
        // createUserDTO.setPortalAccess(createPortalAccessDTOS);

        // then create an AppAccess object for each primary and secondary court of the user
        // Set<CreateAppAccessDTO> createAppAccessDTOS = new HashSet<>(){};

        // for (ImportedNROUser importedNROUser : entry.getValue()) {
        // CreateAppAccessDTO userAppAccess = this.createAppAccessObj(importedNROUser, createUserDTO.getId());
        // if (userAppAccess.getCourtId() != null) {
        // createAppAccessDTOS.add(userAppAccess);
        // }
        // }

        // ONLY add user to the DB, if they have an app access DTO with a court entry that is NOT null
        // if (!(this.usersWithoutCourts.contains(createUserDTO.getEmail()))) {
        // createUserDTO.setAppAccess(createAppAccessDTOS);
        // this.nroUsers.add(createUserDTO);
        // }
        //}
    }

    private ImportedNROUser getNROUser(String[] values) {
        String firstName = values[0];
        String lastName = values[1];
        String email = values[2];

        if ((firstName.isEmpty()) || (lastName.isEmpty()) || (email.isEmpty())) {
            log.info("User with the following values cannot be fully identified and will not be imported: "
                         + Arrays.toString(values));
            return null;
        }

        boolean isDefault;

        // validate isDefault
        if (values[3].toLowerCase().contains("secondary")) {
            isDefault = false;
        } else if (values[3].toLowerCase().contains("primary")) {
            isDefault = true;
        } else {
            this.otherUsersNotImported.add(email);
            return null;
        }

        String court = values[4];
        UUID courtId;

        // validate court
        if (this.courtRepository.findFirstByName(court).isEmpty()) {
            this.usersWithoutCourts.add(email);
            return null;
        } else {
            courtId = this.courtRepository.findFirstByName(court).get().getId();
        }

        String userLevel = values[6];
        UUID roleId;

        // validate role
        if (this.roleRepository.findFirstByName("Level " + userLevel).isEmpty()) {
            this.otherUsersNotImported.add(email);
            return null;
        } else {
            roleId = this.roleRepository.findFirstByName("Level " + userLevel).get().getId();
        }

        return new ImportedNROUser(
            firstName, lastName, email, court, courtId, isDefault, roleId, userLevel
        );
    }
}
