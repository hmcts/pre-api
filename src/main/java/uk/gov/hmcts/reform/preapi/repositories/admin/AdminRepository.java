package uk.gov.hmcts.reform.preapi.repositories.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Searches tables to find what the given UUID is an ID for.
     * Takes the given UUID id and passes it to JDBC Template to replace the placeholders ('?') in the query.
     * The query uses SQL union all to select items across multiple tables (User, Recording, CaptureSession, Booking,
     * Case, Court) that could contain the UUID. If found in that table, it returns a string
     * e.g. if found in the users table, it will return "user". It will return the first match it finds.
     * @param id the UUID to check for
     * @return if found, an Optional containing the type of item the UUID relates to, otherwise empty Optional
     */
    public Optional<String> findUuidType(UUID id) {
        String query = """
            (SELECT 'user' AS table_name FROM users WHERE id = ?
             UNION ALL
             SELECT 'recording' FROM recordings WHERE id = ?
             UNION ALL
             SELECT 'capture_session' FROM capture_sessions WHERE id = ?
             UNION ALL
             SELECT 'booking' FROM bookings WHERE id = ?
             UNION ALL
             SELECT 'case' FROM cases WHERE id = ?
             UNION ALL
             SELECT 'court' FROM courts WHERE id = ?)
             LIMIT 1
            """;

        try {
            String type = jdbcTemplate.queryForObject(query, String.class, id, id, id, id, id, id);
            return Optional.ofNullable(type);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

}
