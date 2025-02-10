package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.RedisService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Handles pre-processing tasks for batch jobs, including loading data into Redis
 * and fetching XML metadata from Azure Blob Storage.
 */
@Component
public class PreProcessor {

    private final RedisService redisService;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;

    private static final String NAMESPACE = "vf:";
    private static final String COURT_KEY_PREFIX = NAMESPACE + "court:";
    private static final String CASE_KEY_PREFIX = NAMESPACE + "case:";
    private static final String USER_KEY_PREFIX = NAMESPACE + "user:";

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
        clearTemporaryStorage();
        Logger.getAnonymousLogger().info("Starting initialization of temporary storage.");
        loadCourtData();
        loadCaseData();
        loadUserData();
        Logger.getAnonymousLogger().info("Initialization completed.");
    }

    /**
     * Clears Redis entries for this batch process to ensure no stale data is used.
     */
    public void clearTemporaryStorage() {
        Logger.getAnonymousLogger().info("Clearing temporary storage...");
        redisService.clearNamespaceKeys(NAMESPACE);
    }

    // =========================
    // Data Loading Logic
    // =========================

    /**
     * Loads all courts from the database into Redis.
     */
    @Transactional
    public void loadCourtData() {
        List<Court> courts = courtRepository.findAll();
        Map<String, String> courtMap = new HashMap<>();
        
        for (Court court : courts) {
            courtMap.put(court.getName(), court.getId().toString());
        }

        redisService.saveHashAll(COURT_KEY_PREFIX, courtMap);
        Logger.getAnonymousLogger().info("Loaded " + courts.size() + " courts into Redis.");
    }

    /**
     * Loads all cases from the database into Redis.
    */
    @Transactional
    public void loadCaseData() {
        List<Case> cases = caseRepository.findAll();
        Map<String, String> caseMap = new HashMap<>();
        for (Case acase : cases) {
            caseMap.put(acase.getReference(), acase.getId().toString());
        }

        redisService.saveHashAll(CASE_KEY_PREFIX, caseMap);
        Logger.getAnonymousLogger().info("Loaded " + cases.size() + " cases into Redis.");
    }

    /**
     * Loads all users from the database into Redis.
    */
    @Transactional
    public void loadUserData() {
        List<User> users = userRepository.findAll();
        Map<String, String> userMap = new HashMap<>();
        for (User user : users) {
            userMap.put(user.getEmail(), user.getId().toString());
        }

        redisService.saveHashAll(USER_KEY_PREFIX, userMap);
        Logger.getAnonymousLogger().info("Loaded " + users.size() + " users into Redis.");
    }
}