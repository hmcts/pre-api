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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class ObscureNROUsers extends RobotUserTask {

    private final AppAccessRepository appAccessRepository;
    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final Set<String> userEmails = new HashSet<>();
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";


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
    }

    public ObscureNROUsers(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail, AppAccessRepository appAccessRepository,
                           CourtRepository courtRepository, RoleRepository roleRepository) {
        super(userService, userAuthenticationService, cronUserEmail);
        this.appAccessRepository = appAccessRepository;
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public void run() throws RuntimeException {
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
            log.info("Error: ", e);
        }

        this.obscureEntries(userEmails);
        log.info("Completed ObscureNROUsers task");
    }

    private void obscureEntries(Set<String> emails) {
        // compose statement to print & input into pgadmin4 to erase the audit logs
        StringBuilder pgAdmin4Query = new StringBuilder("""
                UPDATE public.audits
                SET audit_details = '{}'::jsonb
                WHERE audit_details::text ILIKE '%""");
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

                if (!(userId.toString().isEmpty())) {
                    CreateUserDTO createUserDTO = new CreateUserDTO();
                    Set<CreatePortalAccessDTO> createPortalAccessDTOs = new HashSet<>() {};
                    createUserDTO.setId(userId);
                    createUserDTO.setFirstName("Example");
                    createUserDTO.setLastName("User");
                    createUserDTO.setEmail(userId + "@test.com");
                    createUserDTO.setPortalAccess(createPortalAccessDTOs);

                    Set<CreateAppAccessDTO> createAppAccessDTOs = new HashSet<>() {};
                    // Update app access entries of current user to obscurity
                    if (this.appAccessRepository.findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId)
                        .isPresent()
                        && this.roleRepository.findFirstByName("Level 1").isPresent()
                        && this.courtRepository.findFirstByName("Foo Court").isPresent()) {
                        for (AppAccess appAccess : this.appAccessRepository
                            .findByUser_IdAndDeletedAtNullAndUser_DeletedAtNull(userId).get()) {

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
                        log.info("An app access entry does not exist for "
                                               + createUserDTO.getEmail()
                                               + " Or the role used to obscure the user, or the court used to "
                                               + "obscure the user, does not exist.");
                    }
                }

            } catch (Exception e) {
                log.info(email + " does not exist yet!", e);
            }
        }
        log.info(pgAdmin4Query.toString());
    }
}
