package uk.gov.hmcts.reform.preapi.batch.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.config.batch.BatchConfiguration;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

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
    private static final Logger logger = LoggerFactory.getLogger(BatchConfiguration.class);

    private static final String NAMESPACE = "vf:";
    private static final String COURT_KEY_PREFIX = NAMESPACE + "court:";
    private static final String CASE_KEY_PREFIX = NAMESPACE + "case:";
    private static final String USER_KEY_PREFIX = NAMESPACE + "user:";

    private final RedisService redisService;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    
    @Value("${vodafone-user-email}")
    private String vodafoneUserEmail;

    public PreProcessor(
        RedisService redisService,
        CourtRepository courtRepository,
        CaseRepository caseRepository,
        UserRepository userRepository
    ) {
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
        logger.info("Starting initialization of temporary storage.");
        clearTemporaryStorage();
        loadAllData();
        logger.info("Initialization completed.");
    }


    // ==============================
    // Data Loading Logic & Helpers
    // ==============================

    @Transactional(readOnly = true)
    protected void loadAllData() {   
        List<Court> courts = courtRepository.findAll();
        storeEntitiesInRedis(COURT_KEY_PREFIX, courts, Court::getName, court -> court.getId().toString(),"courts");
        
        List<Case> cases = caseRepository.findAll();
        storeEntitiesInRedis(CASE_KEY_PREFIX, cases, Case::getReference, acase -> acase.getId().toString(),"cases");
        
        List<User> users = userRepository.findAll();
        storeEntitiesInRedis(USER_KEY_PREFIX, users, User::getEmail, user -> user.getId().toString(),"users");
    }

    private <T> void storeEntitiesInRedis(
        String prefix,
        List<T> items,
        Function<T, String> keyFn,
        Function<T, String> valFn,
        String entityName
    ) {
        Map<String, String> map = items.stream()
            .collect(Collectors.toMap(keyFn, valFn, (existing, replacement) -> existing)); 

        redisService.saveHashAll(prefix, map);
        logger.info("Loaded {} {} records into Redis.", items.size(), entityName);
    }

    public void clearTemporaryStorage() {
        logger.info("Clearing temporary storage...");
        redisService.clearNamespaceKeys(NAMESPACE);
    }
}