package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class AddNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final Map<Integer, ImportedNROUser> indexedNROUsers = new HashMap<>();
    private final List<ImportedNROUser> importedNROUsers = new ArrayList<>();
    private final List<CreateUserDTO> nroUsers = new ArrayList<>();
    private final Map<String, String> otherUsersNotImported = new HashMap<>();
    private final RoleRepository roleRepository;
    private final String usersFile;

    @Autowired
    public AddNROUsers(UserService userService,
                       UserAuthenticationService userAuthenticationService,
                       @Value("${cron-user-email}") String cronUserEmail,
                       CourtRepository courtRepository,
                       RoleRepository roleRepository,
                       @Value("${nroUsersFilePath:src/integrationTest/resources/Test_NRO_User_Import.csv}")
                           String usersFile) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.usersFile = usersFile;
    }

    @Override
    public void run() throws RuntimeException {
        log.info("Running AddNROUsers task");
        signInRobotUser(); // needed to populate created_by column in audits table

        log.info("Reading in .csv file from path: {}", this.usersFile);
        try {
            this.createImportedNROUserObjects(this.usersFile);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // removes data for NRO user creation which did not pass initial validation
        this.deleteInvalidNROUsersForUserCreation();

        // create a new user for each valid email (with their corresponding app access entries)
        log.info("Creating new users. . .");
        this.createUsers();

        // validate the new users created
        this.validateCourts();

        // log users which will not be upserted
        for (Map.Entry<String, String> unaddedEmailsAndErrors: this.otherUsersNotImported.entrySet()) {
            log.info(unaddedEmailsAndErrors.getValue());
        }

        log.info("Upserting createUserDTOs to DB. . .");
        for (CreateUserDTO createUserDTOToUpsert : this.nroUsers) {
            // add user to DB (assuming they do not exist already/that there are no other errors)
            try {
                this.userService.upsert(createUserDTOToUpsert);
            } catch (Exception e) {
                // if the upserting of the current user fails, add them to a list of users which have not been uploaded
                this.otherUsersNotImported.put(createUserDTOToUpsert.getEmail(), e.getMessage());
                log.error("Upsert failed for user: {}", createUserDTOToUpsert.getEmail());
                log.error("\nReason for upsert failure:\n{}", e.getMessage());
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

                ImportedNROUser importedNROUser = this.validateNROUser(values, rowNumber);
                if (importedNROUser != null) {
                    this.importedNROUsers.add(importedNROUser);
                }

                rowNumber++;
            }
        } catch (IOException e) {
            log.error("Error: {}", e.getMessage());
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
                || !((this.importedNROUsers.get(index + 1).getEmail()).equals(createUserDTO.getEmail())))) {
                // assign all the app access objects for this user to the current user,
                createUserDTO.setAppAccess(createAppAccessDTOs);
                // then add the user to the list of users to upload
                this.nroUsers.add(createUserDTO);
            }

            index++;
            previousEmail = importedNROUser.getEmail();
        }
    }

    private void deleteInvalidNROUsersForUserCreation() {
        List<ImportedNROUser> importedNROUsersToDelete = new ArrayList<>();
        for (ImportedNROUser importedNROUser : this.importedNROUsers) {
            if (this.otherUsersNotImported.containsKey(importedNROUser.getEmail())) {
                importedNROUsersToDelete.add(importedNROUser);
            }
        }

        for (ImportedNROUser importedNROUserToDelete : importedNROUsersToDelete) {
            this.importedNROUsers.remove(importedNROUserToDelete);
        }
    }

    private Map<UUID, String> getErrorsAndUsersIDsForUsersToDelete(
        List<CreateAppAccessDTO> createAppAccessDTOs) {
        int index = 0;
        UUID previousCourt = null;
        UUID previousUser = null;
        int primaryCourtCount = 0;
        int secondaryCourtCount = 0;
        Map<UUID, String> usersIDsForUsersToDeleteAndErrors = new HashMap<>();

        // for every app access object
        for (CreateAppAccessDTO createAppAccessDTO : createAppAccessDTOs) {
            StringBuilder appAccessErrors = new StringBuilder();

            // the current user is:
            UUID currentUser = createAppAccessDTO.getUserId();

            // if the current iteration has a new user with no primary court logged in the previous iteration,
            if ((!currentUser.equals(previousUser)) && (primaryCourtCount == 0) && (index != 0)) {
                // user does not have a primary court, delete them
                appAccessErrors.append("User has no primary court\n");
                usersIDsForUsersToDeleteAndErrors.put(previousUser, appAccessErrors.toString());
            }

            // the current court is:
            UUID currentCourt = createAppAccessDTO.getCourtId();
            // if the user has the same court name in two different app access objects,
            if ((currentCourt.equals(previousCourt)) && (currentUser.equals(previousUser))) {
                // delete them
                appAccessErrors.append("User has duplicate court names\n");
                usersIDsForUsersToDeleteAndErrors.put(currentUser, appAccessErrors.toString());
            }

            // reset the primaryCourtCount and secondaryCourtCount for new users,
            // and increment primary and secondary court counters if either detected respectively
            List<Integer> courtCountResults = this.incrementCourtCount(primaryCourtCount, secondaryCourtCount,
                                                                       createAppAccessDTO.getDefaultCourt(),
                                                                       currentUser, previousUser);
            primaryCourtCount = courtCountResults.getFirst();
            secondaryCourtCount = courtCountResults.getLast();

            // if a user has more than 4 secondary courts,
            if (secondaryCourtCount > 4) {
                // delete them
                appAccessErrors.append("User has more than 4 secondary courts\n");
                usersIDsForUsersToDeleteAndErrors.put(currentUser, appAccessErrors.toString());
            }

            // if a user has more than 1 primary court,
            if (primaryCourtCount > 1) {
                // delete them
                appAccessErrors.append("User has more than 1 primary court\n");
                usersIDsForUsersToDeleteAndErrors.put(currentUser, appAccessErrors.toString());
            }

            // if the last iteration has a new user who has no primary court,
            if ((index == (createAppAccessDTOs.size() - 1)
                && (!currentUser.equals(previousUser))
                && (primaryCourtCount == 0))) {
                // delete them
                appAccessErrors.append("User has no primary court\n");
                usersIDsForUsersToDeleteAndErrors.put(currentUser, appAccessErrors.toString());
            }

            // re-initialise for the next iteration
            previousCourt = currentCourt;
            previousUser = currentUser;
            index++;
        }
        return usersIDsForUsersToDeleteAndErrors;
    }

    private @Nullable ImportedNROUser getImportedNROUser(int rowNumber, StringBuilder csvErrors,
                                                         ImportedNROUser importedNROUser) {
        String withEmailString = " with email";
        // if errors exist and the user does not have errors already:
        if (!csvErrors.toString().isEmpty() && !this.otherUsersNotImported.containsKey(importedNROUser.getEmail())) {
            csvErrors.insert(0, "User found in row " + rowNumber + " with email '" + importedNROUser.getEmail()
                + "' will not be imported:");
            this.otherUsersNotImported.put(importedNROUser.getEmail(), csvErrors.toString());
            return null;
            // if errors exist and the user has errors already:
        } else if (!csvErrors.toString().isEmpty()
            && this.otherUsersNotImported.containsKey(importedNROUser.getEmail())) {
            csvErrors.insert(0, this.otherUsersNotImported.get(importedNROUser.getEmail())
                .replace(withEmailString, "," + rowNumber + withEmailString));
            this.otherUsersNotImported.put(importedNROUser.getEmail(), csvErrors.toString());
            return null;
        } else if (csvErrors.toString().isEmpty()
            && this.otherUsersNotImported.containsKey(importedNROUser.getEmail())) {
            this.otherUsersNotImported.put(
                importedNROUser.getEmail(), this.otherUsersNotImported.get(importedNROUser.getEmail())
                    .replace(withEmailString, "," + rowNumber + withEmailString));
            return null;
        } else {
            return importedNROUser;
        }
    }

    private List<Integer> incrementCourtCount(int primaryCourtCount, int secondaryCourtCount, boolean isDefaultCourt,
                                              UUID currentUserID, UUID previousUserID) {
        if (!(currentUserID.equals(previousUserID))) {
            primaryCourtCount = 0;
            secondaryCourtCount = 0;
        }

        // increment primary and secondary court counters if either detected respectively
        if (isDefaultCourt) {
            primaryCourtCount++;
        } else {
            secondaryCourtCount++;
        }

        return new ArrayList<>(Arrays.asList(primaryCourtCount, secondaryCourtCount));
    }

    private void validateCourts() {
        // collate all app access DTOs made for the NRO users
        List<CreateAppAccessDTO> createAppAccessDTOs = new ArrayList<>();
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            createAppAccessDTOs.addAll(createUserDTO.getAppAccess());
        }

        // sort app access objects by user ID and then court ID
        createAppAccessDTOs.sort(Comparator.comparing(CreateAppAccessDTO::getUserId)
                                     .thenComparing(CreateAppAccessDTO::getCourtId));

        // collect user IDs to delete (with their reasons)
        Map<UUID, String> usersIDsForUsersToDelete = getErrorsAndUsersIDsForUsersToDelete(createAppAccessDTOs);

        // collect users to delete (with their reasons) with the collected user IDs
        Map<CreateUserDTO, String> usersToDelete = new HashMap<>();
        for (CreateUserDTO createUserDTO : this.nroUsers) {
            if (usersIDsForUsersToDelete.containsKey(createUserDTO.getId())) {
                usersToDelete.put(createUserDTO, usersIDsForUsersToDelete.get(
                    createUserDTO.getId()).substring(0,
                                                     usersIDsForUsersToDelete.get(createUserDTO.getId()).length() - 1));
            }
        }

        // remove users & collect row indexes and emails for logging:
        List<String> usersToDeleteEmails = new ArrayList<>();

        for (Map.Entry<CreateUserDTO, String> userToDelete : usersToDelete.entrySet()) {
            this.nroUsers.remove(userToDelete.getKey());
            usersToDeleteEmails.add(userToDelete.getKey().getEmail());
        }

        Map<String, String> rowIndexesForUsersToDelete = new HashMap<>();

        for (Map.Entry<Integer, ImportedNROUser> indexedNROUser : this.indexedNROUsers.entrySet()) {
            if (usersToDeleteEmails.contains(indexedNROUser.getValue().getEmail())
                && (rowIndexesForUsersToDelete.get(indexedNROUser.getValue().getEmail()) == null)) {
                rowIndexesForUsersToDelete.put(indexedNROUser.getValue().getEmail(),
                                               indexedNROUser.getKey().toString());
            } else if (usersToDeleteEmails.contains(indexedNROUser.getValue().getEmail())
                && (rowIndexesForUsersToDelete.get(indexedNROUser.getValue().getEmail()) != null)
                && (!rowIndexesForUsersToDelete.get(indexedNROUser.getValue().getEmail()).isEmpty())) {
                rowIndexesForUsersToDelete.put(indexedNROUser.getValue().getEmail(),
                                               rowIndexesForUsersToDelete.get(indexedNROUser.getValue().getEmail())
                                                   + "," + indexedNROUser.getKey().toString());
            }
        }

        for (Map.Entry<CreateUserDTO, String> createUserDTOWithErrors : usersToDelete.entrySet()) {
            createUserDTOWithErrors.setValue("User found in row "
                                                 + rowIndexesForUsersToDelete.get(createUserDTOWithErrors.getKey()
                                                                                      .getEmail()) + " with email '"
                                                 + createUserDTOWithErrors.getKey().getEmail()
                                                 + "' will not be imported:\n" + createUserDTOWithErrors.getValue());
            this.otherUsersNotImported.put(createUserDTOWithErrors.getKey().getEmail(), createUserDTOWithErrors
                .getValue());
        }
    }

    private ImportedNROUser validateNROUser(String[] values, int rowNumber) {
        StringBuilder csvErrors = new StringBuilder();
        String fromRowString = " (from row ";

        // validate firstName
        String firstName = values[0];
        if (firstName.isEmpty()) {
            csvErrors.append("\nUser is missing First Name from the .csv input")
                .append(fromRowString)
                .append(rowNumber)
                .append(")");
        }

        // validate lastName
        String lastName = values[1];
        if (lastName.isEmpty()) {
            csvErrors.append("\nUser is missing Last Name from the .csv input")
                .append(fromRowString)
                .append(rowNumber)
                .append(")");
        }

        // validate email
        String email = values[2];
        if (email.isEmpty()) {
            csvErrors.append("\nUser is missing Email from the .csv input")
                .append(fromRowString)
                .append(rowNumber)
                .append(")");
        }

        // validate isDefault
        boolean isDefault = false;

        if (!values[3].toLowerCase().contains("primary") && !values[3].toLowerCase().contains("secondary")) {
            csvErrors.append("\nUser is missing Primary/Secondary Court Level from the .csv input")
                .append(fromRowString)
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
                .append(fromRowString)
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
                .append(fromRowString)
                .append(rowNumber)
                .append(")");
        } else {
            roleID = this.roleRepository.findFirstByName("Level " + userLevel).get().getId();
        }

        this.indexedNROUsers.put(rowNumber, new ImportedNROUser(firstName, lastName, email, court,
                                                                courtID, isDefault, roleID, userLevel));

        return getImportedNROUser(
            rowNumber,
            csvErrors,
            new ImportedNROUser(
            firstName,
            lastName,
            email,
            court,
            courtID,
            isDefault,
            roleID,
            userLevel)
        );
    }
}
