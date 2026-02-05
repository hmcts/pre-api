package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Handles pre-processing tasks for batch jobs, including loading data into Cache
 * and fetching XML metadata from Azure Blob Storage.
 */
@Component
public class PreProcessor {
    private final LoggingService loggingService;
    private final InMemoryCacheService cacheService;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    @Autowired
    public PreProcessor(final LoggingService loggingService,
                        final InMemoryCacheService cacheService,
                        final CourtRepository courtRepository,
                        final CaseRepository caseRepository,
                        final UserRepository userRepository) {
        this.loggingService = loggingService;
        this.cacheService = cacheService;
        this.courtRepository = courtRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // Initialization Logic
    // =========================

    /**
     * Initializes the temporary storage for the batch job.
     * Clears any stale data and loads required entities into Cache.
     */
    public void initialize() {
        loggingService.logInfo("Initiating batch environment preparation.");

        try {
            clearTemporaryStorage();
            cacheRequiredEntities();
            loggingService.logInfo("Batch environment preparation completed successfully.");
        } catch (Exception e) {
            loggingService.logError("Batch environment preparation failed: %s", e);
            throw new IllegalStateException("Failed to prepare batch environment", e);
        }
    }

    // ==============================
    // Data Loading Logic & Helpers
    // ==============================
    protected void cacheRequiredEntities() {
        List<Court> courts = courtRepository.findAll();
        courts.forEach(court -> cacheService.saveCourt(court.getName(), new CourtDTO(court)));
        loggingService.logInfo("Cached %d court records.", courts.size());

        List<Case> cases = caseRepository.findAll();
        cases.forEach(acase -> {
            CreateCaseDTO createCaseDTO = new CreateCaseDTO(acase);
            cacheService.saveCase(acase.getReference(), createCaseDTO);
        });
        loggingService.logInfo("Cached %d cases records.", cases.size());

        List<User> users = userRepository.findAll();
        users.forEach(user -> cacheService.saveUser(user.getEmail(), user.getId()));
        cacheEntity(
            Constants.CacheKeys.USERS_PREFIX,
            users, User::getEmail, user -> user.getId().toString(), "users"
        );
    }

    private <T> void cacheEntity(String prefix,
                                 List<T> items,
                                 Function<T, String> keyFn,
                                 Function<T, String> valFn,
                                 String entityName) {
        if (items.isEmpty()) {
            loggingService.logWarning("No %s found to cache.", entityName);
            return;
        }

        Map<String, Object> map = items.stream()
            .collect(Collectors.toMap(keyFn, valFn, (existing, replacement) -> existing));

        cacheService.saveHashAll(prefix, map);
        loggingService.logInfo("Cached %d %s records.", items.size(), entityName);
    }

    public void clearTemporaryStorage() {
        loggingService.logInfo("Clearing temporary storage...");
        cacheService.clearNamespaceKeys(Constants.CacheKeys.NAMESPACE);
    }
}
