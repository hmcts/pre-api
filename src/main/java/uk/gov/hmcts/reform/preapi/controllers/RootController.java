package uk.gov.hmcts.reform.preapi.controllers;

import org.jooq.*;
import org.jooq.Record; // Starting with Java 14, Record can not be imported on demand anymore
import org.jooq.impl.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.*;

import static org.jooq.generated.Tables.*;
import static org.jooq.impl.DSL.*;
import static org.jooq.SQLDialect.*;
import static org.springframework.http.ResponseEntity.ok;

/**
 * Default endpoints per application.
 */
@RestController
public class RootController {

    /**
     * Root GET endpoint.
     *
     * <p>Azure application service has a hidden feature of making requests to root endpoint when
     * "Always On" is turned on.
     * This is the endpoint to deal with that and therefore silence the unnecessary 404s as a response code.
     *
     * @return Welcome message from the service.
     */
    @GetMapping("/")
    public ResponseEntity<String> welcome() {
        String userName = "postgres";
        String password = "";
        String url = "jdbc:postgresql://localhost:5432/jason";

        String body = "";

        // Connection is the only JDBC resource that we need
        // PreparedStatement and ResultSet are handled by jOOQ, internally
        try (Connection conn = DriverManager.getConnection(url, userName, password)) {
            body += "Connecting to db...<br />\n";
            DSLContext create = DSL.using(conn, POSTGRES);
            Result<Record> result = create.select().from(AUTHOR).fetch();
            body += "Retrieved " + result.size() + " results<br />\n";

            for (Record r : result) {
                Long id = r.getValue(AUTHOR.ID);
                String firstName = r.getValue(AUTHOR.FIRST_NAME);
                String lastName = r.getValue(AUTHOR.LAST_NAME);

                body += "ID: " + id + " first name: " + firstName + " last name: " + lastName + "<br />\n";
            }

        }

        // For the sake of this tutorial, let's keep exception handling simple
        catch (Exception e) {
            e.printStackTrace();
        }

        return ok(body);
    }
}
