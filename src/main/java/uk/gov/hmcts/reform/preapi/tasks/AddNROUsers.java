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
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class AddNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final ArrayList<String> otherUsersNotImported = new ArrayList<>();
    private final ArrayList<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final ArrayList<CreateUserDTO> nroUsers = new ArrayList<>();
    private final RoleRepository roleRepository;
    private Boolean stopScript = false;
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";
    private final UserRepository userRepository;
    private final ArrayList<String> usersWithoutCourts = new ArrayList<>();


    @Autowired
    public AddNROUsers(UserService userService,
                       UserAuthenticationService userAuthenticationService,
                       @Value("${cron-user-email}") String cronUserEmail, CourtRepository courtRepository,
                       RoleRepository roleRepository, UserRepository userRepository,
                       @Value("${testFilePath}") String usersFile) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        if (!(usersFile.isEmpty())) {
            this.usersFile = usersFile;
        }
        this.userRepository = userRepository;
    }

    public AddNROUsers(UserService userService,
                       UserAuthenticationService userAuthenticationService,
                       @Value("${cron-user-email}") String cronUserEmail, CourtRepository courtRepository,
                       RoleRepository roleRepository, UserRepository userRepository) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Running AddNROUsers task");

        log.info("Reading in .csv file. . .");
        this.createImportedNROUserObjects(this.usersFile);
        if (this.stopScript) {
            return;
        }

        // group the new list of NRO users (objects) by email
        log.info("Grouping NRO user courts by email. . .");
        Map<String, List<ImportedNROUser>> groupedByEmail = this.importedNROUsers.stream()
            .collect(Collectors.groupingBy(ImportedNROUser::getEmail));

        // TODO: flatten groupedByEmail to be one list, check if current element contains @
        // if current element contains @, create new user DTO obj, set a UUID and email
        // otherwise, set first name, last name, app access object etc
        // achieve flattening by toString-ing the entryset and splitting it, then iterating through it
        // check chat for solution to flatten the map entirely whilst keeping the key and values beside each other!


        // create a new user for each email,
        log.info("Creating new users. . .");
        for (Map.Entry<String, List<ImportedNROUser>> entry : groupedByEmail.entrySet()) {
            CreateUserDTO createUserDTO = new CreateUserDTO();
            createUserDTO.setId(UUID.randomUUID());
            createUserDTO.setFirstName(entry.getValue().getFirst().getFirstName());
            createUserDTO.setLastName(entry.getValue().getFirst().getLastName());
            createUserDTO.setEmail(entry.getKey());             // entry.getKey() is the user email

            // create a (empty) set of PortalAccess objects for each user
            Set<CreatePortalAccessDTO> createPortalAccessDTOS = new HashSet<>(){};
            createUserDTO.setPortalAccess(createPortalAccessDTOS);

            // then create an AppAccess object for each primary and secondary court of the user
            Set<CreateAppAccessDTO> createAppAccessDTOS = new HashSet<>(){};

            for (ImportedNROUser importedNROUser : entry.getValue()) {
                CreateAppAccessDTO userAppAccess = this.createAppAccessObj(importedNROUser, createUserDTO.getId());
                if (userAppAccess.getCourtId() != null) {
                    createAppAccessDTOS.add(userAppAccess);
                }
            }

            // ONLY add user to the DB, if they have an app access DTO with a court entry that is NOT null
            if (!(this.usersWithoutCourts.contains(createUserDTO.getEmail()))) {
                createUserDTO.setAppAccess(createAppAccessDTOS);
                this.nroUsers.add(createUserDTO);
            }
        }
        log.info("Upserting createUserDTOs to DB. . .");
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            // add user to DB
            try {
                this.userService.upsert(createUserDTO);
            } catch (Exception e) {
                // if the upserting of the current user fails, add them to a list of users which have not been uploaded
                this.otherUsersNotImported.add(createUserDTO.getEmail());
                log.info("Upsert failed for user: {}", createUserDTO.getEmail());
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

    private ImportedNROUser getNROUser(String[] values) {
        String firstName = values[0];
        String lastName = values[1];
        String email = values[2];
        String court = values[4];
        Boolean isDefault = null;
        String userLevel = values[6];

        if (values[3].contains("Secondary")) {
            isDefault = false;
        } else if (values[3].contains("Primary")) {
            isDefault = true;
        } else {
            this.otherUsersNotImported.add(email);
            return null;
        }

        return new ImportedNROUser(
            firstName, lastName, email, court, isDefault, userLevel
        );
    }

    private CreateAppAccessDTO createAppAccessObj(ImportedNROUser importedNROUser, UUID userId) {
        CreateAppAccessDTO userAppAccess = new CreateAppAccessDTO();

        userAppAccess.setId(UUID.randomUUID());

        userAppAccess.setUserId(userId);

        if (this.roleRepository.findFirstByName("Level " + importedNROUser.getUserAccess()).isPresent()) {
            userAppAccess.setRoleId((this.roleRepository.findFirstByName(
                "Level " + importedNROUser.getUserAccess())).get().getId());
        }

        if (this.courtRepository.findFirstByName(importedNROUser.getCourt()).isPresent()) {
            userAppAccess.setCourtId(this.courtRepository.findFirstByName(
                importedNROUser.getCourt()).get().getId());
        } else {
            this.usersWithoutCourts.add(importedNROUser.getEmail());
        }

        userAppAccess.setDefaultCourt(importedNROUser.getIsDefault());

        return userAppAccess;
    }
}
