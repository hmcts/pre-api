package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.entities.AppAccess;
import uk.gov.hmcts.reform.preapi.repositories.AppAccessRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class ObscureNROUsers extends RobotUserTask {

    private final Set<String> userEmails = new HashSet<String>();
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";
    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;


    @Autowired
    public ObscureNROUsers(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail, AppAccessRepository appAccessRepository,
                           CourtRepository courtRepository, RoleRepository roleRepository,
                           @Value("${testFilePath}") String usersFile) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        if (!(usersFile.isEmpty())) {
            this.usersFile = usersFile;
        }
        this.userService = userService;
    }

    public ObscureNROUsers(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail, AppAccessRepository appAccessRepository,
                           CourtRepository courtRepository, RoleRepository roleRepository) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.userService = userService;
    }

    @Override
    public void run() throws RuntimeException {
        // Collate user emails
        try (BufferedReader br = new BufferedReader(new FileReader(usersFile))) {
            String line;
            // Skip header if there is one
            br.readLine(); // Uncomment if your CSV has a header
            // Read each line
            while ((line = br.readLine()) != null) {
                String[] values = parseCsvLine(line);
                String email = values[2];

                this.userEmails.add(email);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        this.obscureEntries(userEmails);
        log.info("Completed ObscureNROUsers task");
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

    private void obscureEntries(Set<String> emails) {
        // compose statement to print & input into pgadmin4 to erase the audit logs
        StringBuilder pgAdmin4Query = new StringBuilder("UPDATE public.audits\n"
                                                            + "SET audit_details = '{}'::jsonb\n"
                                                            + "WHERE audit_details::text ILIKE '%");
        int index = 0;
        for (String email : emails) {
            if (index < (emails.size() - 1)) {
                pgAdmin4Query.append(email)
                    .append("%'\n")
                    .append("OR audit_details::text ILIKE '%");
            } else if (index == (emails.size() - 1)) {
                pgAdmin4Query.append(email)
                    .append("%'");
            }
            index++;

            // Update user with current email to obscurity
            try {
                UUID userId = this.userService.findByEmail(email).getUser().getId(); // User ID of current user

                // System.out.println(userId);
                if (!(userId.toString().isEmpty())) {
                    CreateUserDTO createUserDTO = new CreateUserDTO();
                    Set<CreatePortalAccessDTO> createPortalAccessDTOs = new HashSet<CreatePortalAccessDTO>() {};
                    createUserDTO.setId(userId);
                    createUserDTO.setFirstName("Example");
                    createUserDTO.setLastName("User");
                    createUserDTO.setEmail(userId + "@test.com");
                    createUserDTO.setPortalAccess(createPortalAccessDTOs);

                    Set<CreateAppAccessDTO> createAppAccessDTOs = new HashSet<CreateAppAccessDTO>() {};
                    // Update app access entries of current user to obscurity
                    if (this.appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
                        .isPresent()) {
                        for (AppAccess appAccess : this.appAccessRepository
                            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId).get()) {
                            // System.out.println(createUserDTO.getEmail() + " " + appAccess.getId());

                            CreateAppAccessDTO createAppAccessDTO = new CreateAppAccessDTO();
                            createAppAccessDTO.setId(appAccess.getId());
                            createAppAccessDTO.setUserId(userId);

                            createAppAccessDTO.setDefaultCourt(appAccess.isDefaultCourt());
                            createAppAccessDTO.setRoleId(this.roleRepository.findFirstByName("Level 1")
                                                             .get().getId());
                            createAppAccessDTO.setCourtId(this.courtRepository.findFirstByName("Foo Court")
                                                              .get().getId());

                            createAppAccessDTO.setActive(false);

                            createAppAccessDTOs.add(createAppAccessDTO);
                        }

                        createUserDTO.setAppAccess(createAppAccessDTOs);
                        this.userService.upsert(createUserDTO);
                    } else {
                        System.out.println("New error here");
                    }
                }

            } catch (Exception e) {
                System.out.println(email + " does not exist yet!");
                System.out.println(e);
            }
        }
        log.info(pgAdmin4Query.toString());
        System.out.println(pgAdmin4Query);
    }
}
