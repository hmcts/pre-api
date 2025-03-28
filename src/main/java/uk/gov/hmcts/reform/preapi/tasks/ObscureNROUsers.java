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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@SuppressWarnings("PMD.InsufficientStringBufferDeclaration")
public class ObscureNROUsers extends RobotUserTask {

    private final CourtRepository courtRepository;
    private final RoleRepository roleRepository;
    private final String usersFile;
    private final Map<String, UUID> userEmailAndIDs = new HashMap<>();


    @Autowired
    public ObscureNROUsers(UserService userService,
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
    public void run() {
        UUID obscuringCourtID;
        if (this.courtRepository.findFirstByName("Foo Court").isEmpty()) {
            String noObscuringCourtErrorMessage = "Cannot obscure users: obscuring court does not exist in the DB "
                + "established in the .env file.";
            log.error(noObscuringCourtErrorMessage);
            throw new IllegalArgumentException(noObscuringCourtErrorMessage);
        } else {
            obscuringCourtID = this.courtRepository.findFirstByName("Foo Court").get().getId();
        }

        UUID obscuringRoleID;
        if (this.roleRepository.findFirstByName("Level 4").isEmpty()) {
            String noObscuringRoleErrorMessage = "Cannot obscure users: obscuring role does not exist in the DB "
                + "established in the .env file.";
            log.error(noObscuringRoleErrorMessage);
            throw new IllegalArgumentException(noObscuringRoleErrorMessage);
        } else {
            obscuringRoleID = this.roleRepository.findFirstByName("Level 4").get().getId();
        }

        // Collate user emails
        try (BufferedReader br = Files.newBufferedReader(Path.of(this.usersFile), StandardCharsets.UTF_8)) {
            String line = br.readLine();
            // Read each line
            while (line != null) {
                // Skip header if there is one
                if (line.contains("FirstName")) {
                    line = br.readLine();
                    continue;
                }
                String[] values = ImportedNROUser.parseCsvLine(line);
                String email = values[2];

                this.populateUserEmailsAndIDs(email);
                line = br.readLine();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        this.constructAppAccessQuery(this.userEmailAndIDs.values(), obscuringCourtID, obscuringRoleID);
        this.constructAuditsQuery(this.userEmailAndIDs.keySet());
        this.constructUsersQuery(this.userEmailAndIDs.values());
        log.info("Completed ObscureNROUsers task");
    }

    private void constructAppAccessQuery(Collection<UUID> userIDs, UUID obscuringCourtID, UUID obscuringRoleID) {
        StringBuilder pgAdmin4Query = new StringBuilder("""
                UPDATE public.app_access
                SET
                court_id ='\s""");

        pgAdmin4Query.append(obscuringCourtID)
            .append("',\nrole_id = '")
            .append(obscuringRoleID)
            .append("',\nactive = false\nWHERE user_id IN (");

        for (UUID userID : userIDs) {
            pgAdmin4Query.append('\'').append(userID).append("', ");
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

        List<String> emailsListed = new ArrayList<>(emails);

        pgAdmin4Query.append(emailsListed.getFirst()).append("%'\n");

        // Iterate over remaining emails
        for (int i = 1; i < emailsListed.size(); i++) {
            String email = emailsListed.get(i);
            pgAdmin4Query.append("OR audit_details::text ILIKE '%")
                .append(email)
                .append("%'\n");
        }
        pgAdmin4Query.insert(pgAdmin4Query.length() - 1, ";");

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
            pgAdmin4Query.append('\'').append(userID).append("', ");
        }

        pgAdmin4Query.delete(pgAdmin4Query.length() - 2, pgAdmin4Query.length());
        pgAdmin4Query.append(");");

        log.info(pgAdmin4Query.toString());
    }

    private void populateUserEmailsAndIDs(String email) {
        try {
            this.userEmailAndIDs.put(email, this.userService.findByEmail(email).getUser().getId());
        } catch (NotFoundException | NullPointerException e) {
            log.info("{} does not exist in the DB yet!", email, e);
        }
    }
}
