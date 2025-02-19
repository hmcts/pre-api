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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class AddNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final List<String> otherUsersNotImported = new ArrayList<>();
    private final List<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final List<CreateUserDTO> nroUsers = new ArrayList<>();
    private final RoleRepository roleRepository;
    private Boolean stopScript = false;
    private String usersFile = "src/integrationTest/java/uk/gov/hmcts/reform/preapi/utils/Test_NRO_User_Import.csv";


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

        log.info("Reading in .csv file from path: " + this.usersFile);
        this.createImportedNROUserObjects(this.usersFile);
        // if there were any IO errors in the .csv file, exit
        if (this.stopScript.equals(Boolean.TRUE)) {
            return;
        }

        // create a new user for each email (with their corresponding app access entries)
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
                log.error("Upsert failed for user: {}", createUserDTOToUpsert.getEmail());
                log.error("\nReason for upsert failure:\n", e);
            }
        }

        log.info("NRO Users successfully added to the DB:");
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            if (!this.otherUsersNotImported.contains(createUserDTO.getEmail())) {
                log.info(createUserDTO.getEmail());
            }
        }

        log.info("Completed AddNROUsers task");

    }

    private CreateAppAccessDTO createAppAccessObj(ImportedNROUser importedNROUser, UUID userID) {
        CreateAppAccessDTO userAppAccess = new CreateAppAccessDTO();

        // values have been validated in getNROUser
        userAppAccess.setId(UUID.randomUUID());
        userAppAccess.setUserId(userID);
        userAppAccess.setRoleId(importedNROUser.getRoleID());
        userAppAccess.setCourtId(importedNROUser.getCourtID());
        userAppAccess.setDefaultCourt(importedNROUser.getIsDefault());
        userAppAccess.setActive(true);

        return userAppAccess;
    }

    private void createImportedNROUserObjects(String usersFilePath) {
        int rowNumber = 0;
        // Read from CSV file
        try (BufferedReader br = new BufferedReader(new FileReader(usersFilePath))) {
            String line;
            // Read each line
            while ((line = br.readLine()) != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    continue;
                }

                String[] values = ImportedNROUser.parseCsvLine(line);

                ImportedNROUser importedNROUser = this.getNROUser(values, rowNumber);
                if (importedNROUser != null) {
                    this.importedNROUsers.add(importedNROUser);
                }

                rowNumber++;
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
        Set<CreateAppAccessDTO> createAppAccessDTOs = new HashSet<>(){};
        CreateUserDTO createUserDTO = new CreateUserDTO();
        UUID currentUserID = null;
        int index = 0;
        String previousEmail = null;

        for (ImportedNROUser importedNROUser : this.importedNROUsers) {
            // if the previous email and current email are not the same, make a new user
            if (!Objects.equals(importedNROUser.getEmail(), previousEmail)) {
                currentUserID = UUID.randomUUID();
                createUserDTO = new CreateUserDTO();
                createUserDTO.setId(currentUserID);
                createUserDTO.setFirstName(importedNROUser.getFirstName());
                createUserDTO.setLastName(importedNROUser.getLastName());
                createUserDTO.setEmail(importedNROUser.getEmail());

                // create a (empty) set of PortalAccess objects for each user
                Set<CreatePortalAccessDTO> createPortalAccessDTOS = new HashSet<>(){};
                createUserDTO.setPortalAccess(createPortalAccessDTOS);

                // then create an AppAccess object for each primary and secondary court of the user
                createAppAccessDTOs = new HashSet<>(){};
            }

            CreateAppAccessDTO userAppAccess = this.createAppAccessObj(importedNROUser, currentUserID);
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
    }

    private ImportedNROUser getNROUser(String[] values, int rowNumber) {
        boolean errorFlag = false;

        StringBuilder csvErrors = new StringBuilder();
        csvErrors.append("User in row " + rowNumber + " will not be imported:");

        // validate firstName
        String firstName = values[0];
        if (firstName.isEmpty()) {
            csvErrors.append("\nUser is missing First Name from the .csv input");
            errorFlag = true;
        }

        // validate lastName
        String lastName = values[1];
        if (lastName.isEmpty()) {
            csvErrors.append("\nUser is missing Last Name from the .csv input");
            errorFlag = true;
        }

        // validate email
        String email = values[2];
        if (email.isEmpty()) {
            csvErrors.append("\nUser is missing Email from the .csv input");
            errorFlag = true;
        }

        // validate isDefault
        boolean isDefault = false;

        if (!values[3].toLowerCase().contains("primary") && !values[3].toLowerCase().contains("secondary")) {
            csvErrors.append("\nUser is missing Primary/Secondary Court Level from the .csv input");
            errorFlag = true;
        } else if (values[3].toLowerCase().contains("primary")) {
            isDefault = true;
        }

        // validate court
        String court = values[4];
        UUID courtID = null;
        if (this.courtRepository.findFirstByName(court).isEmpty()) {
            csvErrors.append("\nUser Court from the .csv input does not exist in the DB established in the .env file");
            errorFlag = true;
        } else {
            courtID = this.courtRepository.findFirstByName(court).get().getId();
        }

        // validate role
        String userLevel = values[6];
        UUID roleID = null;
        if (this.roleRepository.findFirstByName("Level " + userLevel).isEmpty()) {
            csvErrors.append("\nUser Role from the .csv input does not exist in the DB established in the .env file");
            errorFlag = true;
        } else {
            roleID = this.roleRepository.findFirstByName("Level " + userLevel).get().getId();
        }

        if (errorFlag) {
            log.info(csvErrors.toString());
            return null;
        } else {
            return new ImportedNROUser(firstName, lastName, email, court, courtID, isDefault, roleID, userLevel);
        }
    }
}
