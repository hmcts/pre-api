package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.InMemoryCacheService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
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
    private LoggingService loggingService;

    private final InMemoryCacheService cacheService;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    public PreProcessor(
        LoggingService loggingService,
        InMemoryCacheService cacheService,
        CourtRepository courtRepository,
        CaseRepository caseRepository,
        UserRepository userRepository
    ) {
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
            throw new RuntimeException("Failed to prepare batch environment", e);
        }
    }


    // ==============================
    // Data Loading Logic & Helpers
    // ==============================

    protected void cacheRequiredEntities() {
        List<Court> courts = courtRepository.findAll();
        cacheEntity(
            Constants.CacheKeys.COURTS_PREFIX,
            courts, Court::getName, court -> court.getId().toString(), "courts"
        );

        List<Case> cases = caseRepository.findAll();
        cacheEntity(
            Constants.CacheKeys.CASES_PREFIX,
            cases, Case::getReference, acase -> acase.getId().toString(), "cases"
        );

        List<User> users = userRepository.findAll();
        cacheEntity(
            Constants.CacheKeys.USERS_PREFIX,
            users, User::getEmail, user -> user.getId().toString(), "users"
        );
    }

    private <T> void cacheEntity(
        String prefix,
        List<T> items,
        Function<T, String> keyFn,
        Function<T, String> valFn,
        String entityName
    ) {
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
