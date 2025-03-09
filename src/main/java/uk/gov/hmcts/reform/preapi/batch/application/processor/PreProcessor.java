package uk.gov.hmcts.reform.preapi.batch.application.processor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.batch.application.services.persistence.RedisService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
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
 * Handles pre-processing tasks for batch jobs, including loading data into Redis
 * and fetching XML metadata from Azure Blob Storage.
 */
@Component
public class PreProcessor {
    private LoggingService loggingService;

    private static final class RedisNamespaces {
        private static final String NAMESPACE = "vf:";
        private static final String COURT_KEY_PREFIX = NAMESPACE + "court:";
        private static final String CASE_KEY_PREFIX = NAMESPACE + "case:";
        private static final String USER_KEY_PREFIX = NAMESPACE + "user:";
    }

    private final RedisService redisService;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    
    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    public PreProcessor(
        LoggingService loggingService,
        RedisService redisService,
        CourtRepository courtRepository,
        CaseRepository caseRepository,
        UserRepository userRepository
    ) {
        this.loggingService = loggingService;
        this.redisService = redisService;
        this.courtRepository = courtRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // Initialization Logic
    // =========================

    /**
     * Initializes the temporary storage for the batch job.
     * Clears any stale data and loads required entities into Redis.
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

    @Transactional(readOnly = true)
    protected void cacheRequiredEntities() {   
        List<Court> courts = courtRepository.findAll();
        cacheEntityToRedis(
            RedisNamespaces.COURT_KEY_PREFIX, 
            courts, Court::getName, court -> court.getId().toString(),"courts");
        
        List<Case> cases = caseRepository.findAll();
        cacheEntityToRedis(
            RedisNamespaces.CASE_KEY_PREFIX, 
            cases, Case::getReference, acase -> acase.getId().toString(),"cases");
        
        List<User> users = userRepository.findAll();
        cacheEntityToRedis(
            RedisNamespaces.USER_KEY_PREFIX, 
            users, User::getEmail, user -> user.getId().toString(),"users");
    }

    private <T> void cacheEntityToRedis(
        String prefix,
        List<T> items,
        Function<T, String> keyFn,
        Function<T, String> valFn,
        String entityName
    ) {
        if (items.isEmpty()) {
            loggingService.logWarning("No %s found to cache in Redis.", entityName);
            return;
        }

        Map<String, String> map = items.stream()
            .collect(Collectors.toMap(keyFn, valFn, (existing, replacement) -> existing)); 

        redisService.saveHashAll(prefix, map);
        loggingService.logInfo("Cached %d %s records into Redis.", items.size(), entityName);
    }

    public void clearTemporaryStorage() {
        loggingService.logInfo("Clearing temporary storage...");
        redisService.clearNamespaceKeys(RedisNamespaces.NAMESPACE);
    }
}