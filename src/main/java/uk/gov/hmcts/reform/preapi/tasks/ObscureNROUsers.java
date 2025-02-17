package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ObscureNROUsers extends RobotUserTask {

    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final Set<String> userEmails = new HashSet<>();
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";
    private UUID obscuringRoleID;
    private UUID obscuringCourtID;


    @Autowired
    public ObscureNROUsers(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail, AppAccessRepository appAccessRepository,
                           CourtRepository courtRepository, RoleRepository roleRepository,
                           @Value("${nroUsersFilePath}") String usersFile) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.usersFile = usersFile;
    }

    @Override
    public void run() throws RuntimeException {

        if (this.roleRepository.findFirstByName("Level 1").isEmpty()
            || this.courtRepository.findFirstByName("Foo Court").isEmpty()) {
            log.error("Cannot obscure users: obscuring role and/or court do not exist in the DB.");
            return;
        } else {
            this.obscuringRoleID = this.roleRepository.findFirstByName("Level 1").get().getId();
            this.obscuringCourtID = this.courtRepository.findFirstByName("Foo Court").get().getId();
        }

        // Collate user emails
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
            String line;
            // Read each line
            while ((line = br.readLine()) != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    continue;
                }
                String[] values = ImportedNROUser.parseCsvLine(line);
                String email = values[2];

                this.userEmails.add(email);
            }
        } catch (IOException e) {
            log.error("Error: " + e.getMessage());
            return;
        }

        this.obscureEntries(userEmails);
        this.constructQuery(userEmails);
        log.info("Completed ObscureNROUsers task");
    }

    private void obscureEntries(Set<String> emails) {

        ArrayList<List<AppAccess>> unflattenedAppAccessObjsForImportedUsers = new ArrayList<>();
        Map<String, UUID> userEmailAndIDs = new HashMap<>();

        for (String email : emails) {
            try {
                userEmailAndIDs.put(email, this.userService.findByEmail(email).getUser().getId());
            } catch (NotFoundException e) {
                log.info(email + " does not exist in the DB yet!", e);
            }
        }

        for (Map.Entry<String, UUID> emailAndID : userEmailAndIDs.entrySet()) {
            if (this.appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(emailAndID.getValue())
                .isEmpty()) {
                log.info("An app access entry cannot be found for " + emailAndID.getKey());
            } else {
                unflattenedAppAccessObjsForImportedUsers.add(
                    this.appAccessRepository.findAllByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(
                        emailAndID.getValue()));
            }
        }

        ArrayList<AppAccess> appAccessObjsForImportedUsers = unflattenedAppAccessObjsForImportedUsers.stream()
            .flatMap(List::stream)
            .collect(Collectors.toCollection(ArrayList::new));

        // initialise values
        Set<CreateAppAccessDTO> createAppAccessDTOs = null;
        CreateUserDTO createUserDTO = null;
        int index = 0;
        UUID previousUserID = null;

        // iterate over each app access entry for each imported user, to alter their values & those of their user
        for (AppAccess appAccess : appAccessObjsForImportedUsers) {
            if (appAccess.getUser().getId() != previousUserID) {
                createUserDTO = new CreateUserDTO();
                Set<CreatePortalAccessDTO> createPortalAccessDTOs = new HashSet<>() {};
                createUserDTO.setId(appAccess.getUser().getId());
                createUserDTO.setFirstName("Example");
                createUserDTO.setLastName("User");
                createUserDTO.setEmail(appAccess.getUser().getId() + "@test.com");
                createUserDTO.setPortalAccess(createPortalAccessDTOs);
                createAppAccessDTOs = new HashSet<>() {};
            }

            CreateAppAccessDTO userAppAccess = this.getCreateAppAccessDTO(appAccess, appAccess.getUser().getId());
            createAppAccessDTOs.add(userAppAccess);

            // if this is the last element, or if the next element is a new email,
            if ((index == (appAccessObjsForImportedUsers.size() - 1)
                || !(Objects.equals(appAccessObjsForImportedUsers.get(index + 1).getUser().getEmail(),
                                    createUserDTO.getEmail())))) {
                // assign all the app access objects for this user to the current user
                createUserDTO.setAppAccess(createAppAccessDTOs);
                this.userService.upsert(createUserDTO);
            }
            index++;
            previousUserID = appAccess.getUser().getId();
        }
    }

    private CreateAppAccessDTO getCreateAppAccessDTO(AppAccess appAccess, UUID userId) {
        CreateAppAccessDTO createAppAccessDTO = new CreateAppAccessDTO();
        createAppAccessDTO.setId(appAccess.getId());
        createAppAccessDTO.setUserId(userId);

        createAppAccessDTO.setDefaultCourt(appAccess.isDefaultCourt());
        createAppAccessDTO.setRoleId(this.obscuringRoleID);
        createAppAccessDTO.setCourtId(this.obscuringCourtID);

        createAppAccessDTO.setActive(false);
        return createAppAccessDTO;
    }

    private void constructQuery(Set<String> emails) {
        // compose statement to print & input into pgadmin4 to erase the audit logs
        StringBuilder pgAdmin4Query = new StringBuilder("""
                UPDATE public.audits
                SET audit_details = '{}'::jsonb
                WHERE audit_details::text ILIKE '%""");

        ArrayList<String> emailsListed = new ArrayList<>(emails);

        pgAdmin4Query.append(emailsListed.getFirst()).append("%'\n");

        // Iterate over remaining emails
        for (int i = 1; i < emailsListed.size(); i++) {
            String email = emailsListed.get(i);
            pgAdmin4Query.append("OR audit_details::text ILIKE '%")
                .append(email)
                .append("%'\n");
        }

        log.info(pgAdmin4Query.toString());
    }
}
