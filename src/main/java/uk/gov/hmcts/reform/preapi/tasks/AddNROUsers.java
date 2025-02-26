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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class AddNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final Map<String, String> otherUsersNotImported = new HashMap<>();
    private final Map<Integer, ImportedNROUser> indexedNROUsers = new HashMap<>();
    private final List<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final List<CreateUserDTO> nroUsers = new ArrayList<>();
    private final RoleRepository roleRepository;
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
        signInRobotUser();

        log.info("Reading in .csv file from path: " + this.usersFile);
        try {
            this.createImportedNROUserObjects(this.usersFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        this.deleteInvalidNROUsersForImportation();

        // create a new user for each email (with their corresponding app access entries)
        log.info("Creating new users. . .");
        this.createUsers();

        this.removeDuplicates();

        log.info("Upserting createUserDTOs to DB. . .");
        for (CreateUserDTO createUserDTOToUpsert : this.nroUsers) {
            // add user to DB (assuming they do not exist already)
            try {
                this.userService.upsert(createUserDTOToUpsert);
            } catch (Exception e) {
                // if the upserting of the current user fails, add them to a list of users which have not been uploaded
                this.otherUsersNotImported.put(createUserDTOToUpsert.getEmail(), e.getMessage());
                log.error("Upsert failed for user: {}", createUserDTOToUpsert.getEmail());
                log.error("\nReason for upsert failure:\n" + e.getMessage());
            }
        }

        log.info("NRO Users successfully added to the DB:");
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            if (!this.otherUsersNotImported.containsKey(createUserDTO.getEmail())) {
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

    private void createImportedNROUserObjects(String usersFilePath) throws IOException {
        int rowNumber = 1;
        // Read from CSV file
        try (BufferedReader br = new BufferedReader(new FileReader(usersFilePath))) {
            String line;
            // Read each line
            while ((line = br.readLine()) != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    rowNumber++;
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
            throw e;
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
            if (!importedNROUser.getEmail().equals(previousEmail)) {
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

    private void deleteInvalidNROUsersForImportation() {
        List<ImportedNROUser> importedNROUsersToDelete = new ArrayList<>();
        for (ImportedNROUser importedNROUser : this.importedNROUsers) {
            if (this.otherUsersNotImported.containsKey(importedNROUser.getEmail())) {
                importedNROUsersToDelete.add(importedNROUser);
            }
        }

        for (ImportedNROUser importedNROUserToDelete : importedNROUsersToDelete) {
            this.importedNROUsers.remove(importedNROUserToDelete);
        }

        for (Map.Entry<String, String> unaddedEmailsAndErrors: this.otherUsersNotImported.entrySet()) {
            log.info(unaddedEmailsAndErrors.getValue());
        }
    }

    private ImportedNROUser getNROUser(String[] values, int rowNumber) {
        StringBuilder csvErrors = new StringBuilder();

        // validate firstName
        String firstName = values[0];
        if (firstName.isEmpty()) {
            csvErrors.append("\nUser is missing First Name from the .csv input")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        }

        // validate lastName
        String lastName = values[1];
        if (lastName.isEmpty()) {
            csvErrors.append("\nUser is missing Last Name from the .csv input")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        }

        // validate email
        String email = values[2];
        if (email.isEmpty()) {
            csvErrors.append("\nUser is missing Email from the .csv input")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        }

        // validate isDefault
        boolean isDefault = false;

        if (!values[3].toLowerCase().contains("primary") && !values[3].toLowerCase().contains("secondary")) {
            csvErrors.append("\nUser is missing Primary/Secondary Court Level from the .csv input")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        } else if (values[3].toLowerCase().contains("primary")) {
            isDefault = true;
        }

        // validate court
        String court = values[4];
        UUID courtID = null;
        if (this.courtRepository.findFirstByName(court).isEmpty()) {
            csvErrors.append("\nUser Court from the .csv input does not exist in the DB established in the .env file")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        } else {
            courtID = this.courtRepository.findFirstByName(court).get().getId();
        }

        // validate role
        String userLevel = values[6];
        UUID roleID = null;
        if (this.roleRepository.findFirstByName("Level " + userLevel).isEmpty()) {
            csvErrors.append("\nUser Role from the .csv input does not exist in the DB established in the .env file")
                .append(" (from row ")
                .append(rowNumber)
                .append(")");
        } else {
            roleID = this.roleRepository.findFirstByName("Level " + userLevel).get().getId();
        }

        this.indexedNROUsers.put(rowNumber, new ImportedNROUser(firstName, lastName, email, court,
                                                                courtID, isDefault, roleID, userLevel));

        // if errors exist and the user does not have errors already:
        if (!csvErrors.toString().isEmpty() && !this.otherUsersNotImported.containsKey(email)) {
            csvErrors.insert(0, "User found in row " + rowNumber + " with email '" + email
                + "' will not be imported:");
            this.otherUsersNotImported.put(email, csvErrors.toString());
            return null;
        // if errors exist and the user has errors already:
        } else if (!csvErrors.toString().isEmpty() && this.otherUsersNotImported.containsKey(email)) {
            csvErrors.insert(0, this.otherUsersNotImported.get(email)
                .replace(" with email", "," + rowNumber + " with email"));
            this.otherUsersNotImported.put(email, csvErrors.toString());
            return null;
        } else if (csvErrors.toString().isEmpty() && this.otherUsersNotImported.containsKey(email)) {
            this.otherUsersNotImported.put(email, this.otherUsersNotImported.get(email)
                .replace(" with email", "," + rowNumber + " with email"));
            return null;
        } else {
            return new ImportedNROUser(firstName, lastName, email, court, courtID, isDefault, roleID, userLevel);
        }
    }

    private void removeDuplicates() {
        // collate all app access DTOs made for the NRO users
        List<CreateAppAccessDTO> createAppAccessDTOs = new ArrayList<>();
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            createAppAccessDTOs.addAll(createUserDTO.getAppAccess());
        }

        // sort app access objects by user ID and then court ID
        createAppAccessDTOs.sort(Comparator.comparing(CreateAppAccessDTO::getUserId)
                        .thenComparing(CreateAppAccessDTO::getCourtId));

        // initialise values
        int index = 0;
        UUID previousCourt = null;
        UUID previousUser = null;
        int secondaryCourtCount = 0;
        int primaryCourtCount = 0;
        boolean userHasPrimaryCourt = false;
        Map<UUID, String> usersIDsForUsersToDelete = new HashMap<>();

        // for every app access object
        for (CreateAppAccessDTO createAppAccessDTO : createAppAccessDTOs) {
            StringBuilder appAccessErrors = new StringBuilder();

            // the current user is:
            UUID currentUser = createAppAccessDTO.getUserId();

            if ((currentUser != previousUser) && !userHasPrimaryCourt && (index != 0)) {
                // user does not have a primary court, delete them
                appAccessErrors.append("\nUser has no primary court");
                usersIDsForUsersToDelete.put(previousUser, appAccessErrors.toString());
            }

            // reset the secondaryCourtCount for new users
            if ((currentUser != previousUser)) {
                secondaryCourtCount = 0;
                primaryCourtCount = 0;
                userHasPrimaryCourt = false;
            }

            // the current court is:
            UUID currentCourt = createAppAccessDTO.getCourtId();
            // if the user has the same court name in two different app access objects,
            if ((currentCourt == previousCourt) && (currentUser == previousUser)) {
                // delete them
                appAccessErrors.append("\nUser has duplicate court names");
                usersIDsForUsersToDelete.put(currentUser, appAccessErrors.toString());
            }

            // if the user has a primary court,
            if (createAppAccessDTO.getDefaultCourt()) {
                // set userHasPrimaryCourt to true & decrement the secondaryCourtCount
                userHasPrimaryCourt = true;
                primaryCourtCount++;
            } else {
                secondaryCourtCount++;
            }

            // if a user has more than 4 secondary courts,
            if (secondaryCourtCount > 4) {
                // delete them
                appAccessErrors.append("\nUser has more than 4 secondary courts");
                usersIDsForUsersToDelete.put(currentUser, appAccessErrors.toString());
            }

            if (primaryCourtCount > 1) {
                appAccessErrors.append("\nUser has more than 1 primary court");
                usersIDsForUsersToDelete.put(currentUser, appAccessErrors.toString());
            }

            // re-initialise for the next iteration
            previousCourt = currentCourt;
            previousUser = currentUser;
            index++;
        }

        Map<CreateUserDTO, String> usersToDelete = new HashMap<>();
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            if (usersIDsForUsersToDelete.containsKey(createUserDTO.getId())) {
                usersToDelete.put(createUserDTO, usersIDsForUsersToDelete.get(createUserDTO.getId()));
            }
        }

        for (Map.Entry<CreateUserDTO, String> userToDelete : usersToDelete.entrySet()) {
            this.nroUsers.remove(userToDelete.getKey());
        }
    }

    private int findRowNumber(CreateAppAccessDTO createAppAccessDTO, String userEmail) {
        for (Map.Entry<Integer, ImportedNROUser> indexedNROUser : this.indexedNROUsers.entrySet()) {
            if ((Objects.equals(indexedNROUser.getValue().getEmail(), userEmail))
                && (indexedNROUser.getValue().getIsDefault() == createAppAccessDTO.getDefaultCourt())
                && (Objects.equals(indexedNROUser.getValue().getUserAccess(), createAppAccessDTO.getRoleId().toString())
                && (indexedNROUser.getValue().getCourtID() == createAppAccessDTO.getCourtId()))) {
                return indexedNROUser.getKey();
            }
        }
        return 0;
    }

    private String findUserEmail(CreateAppAccessDTO createAppAccessDTO) {
        UUID userID = createAppAccessDTO.getUserId();
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            if (createUserDTO.getId() == userID) {
                return createUserDTO.getEmail();
            }
        }
        return null;
    }
}
