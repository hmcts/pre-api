package uk.gov.hmcts.reform.preapi.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
@Slf4j
public class ObscureNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private String usersFile = "src/main/java/uk/gov/hmcts/reform/preapi/tasks/NRO_User_Import.csv";
    private final UUID obscuringRoleID;
    private final UUID obscuringCourtID;
    private final Map<String, UUID> userEmailAndIDs = new HashMap<>();


    @Autowired
    public ObscureNROUsers(UserService userService,
                           UserAuthenticationService userAuthenticationService,
                           @Value("${cron-user-email}") String cronUserEmail,
                           CourtRepository courtRepository, RoleRepository roleRepository,
                           @Value("${nroUsersFilePath}") String usersFile) throws IllegalArgumentException {
        super(userService, userAuthenticationService, cronUserEmail);
        this.courtRepository = courtRepository;
        this.roleRepository = roleRepository;
        this.usersFile = usersFile;

        if (this.roleRepository.findFirstByName("Level 4").isEmpty()) {
            String noObscuringRoleErrorMessage = "Cannot obscure users: obscuring role does not exist in the DB "
                + "established in the .env file.";
            log.error(noObscuringRoleErrorMessage);
            throw new IllegalArgumentException(noObscuringRoleErrorMessage);
        } else {
            this.obscuringRoleID = this.roleRepository.findFirstByName("Level 4").get().getId();
        }

        if (this.courtRepository.findFirstByName("Foo Court").isEmpty()) {
            String noObscuringCourtErrorMessage = "Cannot obscure users: obscuring court does not exist in the DB "
                + "established in the .env file.";
            log.error(noObscuringCourtErrorMessage);
            throw new IllegalArgumentException(noObscuringCourtErrorMessage);
        } else {
            this.obscuringCourtID = this.courtRepository.findFirstByName("Foo Court").get().getId();
        }
    }

    @Override
    public void run() throws RuntimeException {

        // Collate user emails
        try (BufferedReader br = new BufferedReader(new FileReader(this.usersFile))) {
            String line;
            // Read each line
            while ((line = br.readLine()) != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    continue;
                }
                String[] values = ImportedNROUser.parseCsvLine(line);
                String email = values[2];

                try {
                    this.userEmailAndIDs.put(email, this.userService.findByEmail(email).getUser().getId());
                } catch (NotFoundException e) {
                    log.info(email + " does not exist in the DB yet!", e);
                }
            }
        } catch (IOException e) {
            log.error("Error: " + e.getMessage());
            return;
        }

        this.constructAppAccessQuery(this.userEmailAndIDs.values(), this.obscuringCourtID, this.obscuringRoleID);
        this.constructAuditsQuery(this.userEmailAndIDs.keySet());
        this.constructUsersQuery(this.userEmailAndIDs.values());
        log.info("Completed ObscureNROUsers task");
    }

    private void constructAppAccessQuery(Collection<UUID> userIDs, UUID obscuringCourtID, UUID obscuringRoleID) {
        StringBuilder pgAdmin4Query = new StringBuilder("""
                UPDATE public.app_access
                SET
                court_id =\s""");

        pgAdmin4Query.append("'").append(obscuringCourtID).append("',\n");
        pgAdmin4Query.append("role_id = '").append(obscuringRoleID).append("',\n");
        pgAdmin4Query.append("active = false\nWHERE user_id IN (");

        for (UUID userID : userIDs) {
            pgAdmin4Query.append("'").append(userID).append("', ");
        }

        pgAdmin4Query.delete(pgAdmin4Query.length() - 2, pgAdmin4Query.length());
        pgAdmin4Query.append(");");

        log.info(pgAdmin4Query.toString());
    }

    private void constructAuditsQuery(Set<String> emails) {
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

    private void constructUsersQuery(Collection<UUID> userIDs) {
        StringBuilder pgAdmin4Query = new StringBuilder("""
                UPDATE public.users
                SET
                first_name = 'Example',
                last_name = 'User',
                email = CONCAT(id::text, '@example.com')
                WHERE id IN (""");

        for (UUID userID : userIDs) {
            pgAdmin4Query.append("'").append(userID).append("', ");
        }

        pgAdmin4Query.delete(pgAdmin4Query.length() - 2, pgAdmin4Query.length());
        pgAdmin4Query.append(");");

        log.info(pgAdmin4Query.toString());
    }
}
