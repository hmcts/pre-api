package uk.gov.hmcts.reform.preapi.services.admin;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.repositories.admin.AdminRepository;

import java.util.Objects;
import java.util.UUID;

@Service
public class AdminService {

    private final AdminRepository adminRepository;

    @Autowired
    public AdminService(AdminRepository adminRepository) {
        this.adminRepository = adminRepository;
    }

    /**
     * Checks if a UUID exists in relevant tables.
     * Uses the AdminRepository to search the database for the UUID. The String returned from the repository
     * is then converted to an enum value.
     * @param id the UUID to check the tables for
     * @return a string indicating the type of table related to the UUID
     * @throws NotFoundException if the UUID does not exist in the tables being checked
     */
    public UuidTableType findUuidType(UUID id) {

        String tableName = adminRepository.findUuidType(Objects.requireNonNull(id, "UUID should not be null"))
            .orElseThrow(() -> new NotFoundException(id + " does not exist in any relevant table"));

        return UuidTableType.valueOf(tableName.toUpperCase());
    }

    /**
     * Enum representing the tables the UUID could belong to. Restricts to relevant tables only.
     */
    @Getter
    public enum UuidTableType {
        USER,
        RECORDING,
        CAPTURE_SESSION,
        BOOKING,
        CASE,
        COURT
    }
}
