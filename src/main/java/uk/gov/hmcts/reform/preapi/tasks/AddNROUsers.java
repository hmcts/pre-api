package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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
    private final ArrayList<CreateUserDTO> existingUsers = new ArrayList<>();
    private final ArrayList<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final ArrayList<CreateUserDTO> nroUsers = new ArrayList<>();
    private final RoleRepository roleRepository;
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";
    private final UserRepository userRepository;
    private final UserService userService;
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
        this.userService = userService;
    }

    public AddNROUsers(UserService userService,
                       UserAuthenticationService userAuthenticationService,
                       @Value("${cron-user-email}") String cronUserEmail, CourtRepository courtRepository,
                       RoleRepository roleRepository, UserRepository userRepository) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Running AddNROUsers task");

        System.out.println("Reading in .csv file. . .");
        this.createImportedNROUserObjects(this.usersFile);

        // group the new list of NRO users (objects) by email
        System.out.println("Grouping NRO user courts by email. . .");
        Map<String, List<ImportedNROUser>> groupedByEmail = this.importedNROUsers.stream()
            .collect(Collectors.groupingBy(ImportedNROUser::getEmail));

        // TODO: flatten groupedByEmail to be one list, check if current element contains @
        // if current element contains @, create new user DTO obj, set a UUID and email
        // otherwise, set first name, last name, app access object etc
        // achieve flattening by toString-ing the entryset and splitting it, then iterating through it
        // check chat for solution to flatten the map entirely whilst keeping the key and values beside each other!


        // create a new user for each email,
        System.out.println("Creating new users. . .");
        for (Map.Entry<String, List<ImportedNROUser>> entry : groupedByEmail.entrySet()) {
            CreateUserDTO createUserDTO = new CreateUserDTO();
            createUserDTO.setId(UUID.randomUUID());
            createUserDTO.setFirstName(entry.getValue().getFirst().getFirstName());
            createUserDTO.setLastName(entry.getValue().getFirst().getLastName());
            createUserDTO.setEmail(entry.getKey());             // entry.getKey() is the user email

            // create a (empty) set of PortalAccess objects for each user
            // System.out.println("Creating new portal access object. . .");
            Set<CreatePortalAccessDTO> createPortalAccessDTOS = new HashSet<>(){};
            createUserDTO.setPortalAccess(createPortalAccessDTOS);

            // then create an AppAccess object for each primary and secondary court of the user
            // System.out.println("Creating new app access object(s). . .");
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
        System.out.println("Upserting createUserDTOs to DB. . .");
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            // add user to DB
            // System.out.println("Upserting " + createUserDTO.getEmail() + " to DB. . .");
            try {
                this.userService.upsert(createUserDTO);
            } catch (Exception e) {
                // if the upserting of the current user fails, add them to a list of users which have not been uploaded
                this.existingUsers.add(createUserDTO);
            }

            // check user is in DB
            if (this.userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(createUserDTO.getEmail()).isEmpty()) {
                log.info("Upsert failed for user: {}", createUserDTO.getEmail());
            }
        }

        System.out.println("Uninserted users without courts:");
        for (String importedNROUserEmail : this.usersWithoutCourts) {
            System.out.println(importedNROUserEmail);
        }
        System.out.println("Otherwise uninserted users:");
        for (CreateUserDTO uninsertedUser : this.existingUsers) {
            System.out.println(uninsertedUser.getEmail());
        }

        log.info("Completed AddNROUsers task");

    }

    private String[] parseCsvLine(String line) {
        ArrayList<String> result = new ArrayList<>();
        StringBuilder currentValue = new StringBuilder();
        boolean inQuotes = false;

        for (char ch : line.toCharArray()) {
            if (ch == '\"') {
                inQuotes = !inQuotes;  // Toggle the inQuotes flag
            } else if (ch == ',' && !inQuotes) {
                result.add(currentValue.toString().trim());
                currentValue.setLength(0); // Reset the StringBuilder
            } else {
                currentValue.append(ch);
            }
        }
        // Add the last value
        result.add(currentValue.toString().trim());

        return result.toArray(new String[0]);
    }

    private void createImportedNROUserObjects(String usersFilePath) {
        // Read from CSV file
        try (BufferedReader br = new BufferedReader(new FileReader(usersFilePath))) {
            String line;
            // Skip header if there is one
            br.readLine(); // Uncomment if your CSV has a header

            // Read each line
            while ((line = br.readLine()) != null) {
                String[] values = parseCsvLine(line);

                ImportedNROUser importedNROUser = getNROUser(values);

                this.importedNROUsers.add(importedNROUser);
            }
        } catch (IOException e) {
            log.info("Error: ", e);
            System.exit(0);
        }
    }

    private static @NotNull ImportedNROUser getNROUser(String[] values) {
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
        }

        ImportedNROUser importedNROUser = new ImportedNROUser(
            firstName, lastName, email, court, isDefault, userLevel
        );
        return importedNROUser;
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
