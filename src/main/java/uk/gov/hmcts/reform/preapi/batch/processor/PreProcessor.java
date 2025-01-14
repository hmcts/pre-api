package uk.gov.hmcts.reform.preapi.batch.processor;

import org.springframework.core.io.InputStreamResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.repositories.CourtRepository;
import uk.gov.hmcts.reform.preapi.repositories.UserRepository;
import uk.gov.hmcts.reform.preapi.services.batch.AzureBlobService;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

@Component
public class PreProcessor {

    private final RedisTemplate<String, Object> redisTemplate;
    private final CourtRepository courtRepository;
    private final CaseRepository caseRepository;
    private final UserRepository userRepository;
    private final AzureBlobService azureBlobFetcher;

    private static final String NAMESPACE = "batch-preprocessor:";
    private static final String COURT_KEY_PREFIX = NAMESPACE + "court:";
    private static final String CASE_KEY_PREFIX = NAMESPACE + "case:";
    private static final String USER_KEY_PREFIX = NAMESPACE + "user:";
    private static final String XML_RESOURCES_KEY = NAMESPACE + "xmlResources";

    public PreProcessor(
        RedisTemplate<String, Object> redisTemplate,
        CourtRepository courtRepository,
        CaseRepository caseRepository,
        UserRepository userRepository,
        AzureBlobService azureBlobFetcher
    ) {
        this.redisTemplate = redisTemplate;
        this.courtRepository = courtRepository;
        this.caseRepository = caseRepository;
        this.userRepository = userRepository;
        this.azureBlobFetcher = azureBlobFetcher;
    }

    /**
     * Initializes the temporary storage for the batch job.
     * Clears any stale data and loads required entities into Redis.
     */
    public void initialize() {
        clearTemporaryStorage();
        Logger.getAnonymousLogger().info("PreProcessor: Starting initialisation of temporary storage.");
        loadCourtData();
        loadCaseData();
        loadUserData();
        fetchAndCacheXmlMetadata("pre-vodafone-spike");
        Logger.getAnonymousLogger().info("PreProcessor: Initialisation completed.");
    }

    /**
     * Clears Redis entries for this batch process to ensure no stale data is used.
     */
    public void clearTemporaryStorage() {
        Logger.getAnonymousLogger().info("PreProcessor: Clearing temporary storage...");
        redisTemplate.keys(NAMESPACE + "*").forEach(redisTemplate::delete);
        Logger.getAnonymousLogger().info("PreProcessor: Temporary storage cleared.");
    }

    /**
     * Loads all courts from the database into Redis.
    */
    @Transactional
    public void loadCourtData() {
        List<Court> courts = courtRepository.findAll();
        for (Court court : courts) {
            String courtKey = COURT_KEY_PREFIX + court.getName();
            redisTemplate.opsForValue().set(courtKey, court.getId().toString());
        }
    }

    /**
     * Loads all cases from the database into Redis.
    */
    @Transactional
    public void loadCaseData() {
        List<Case> cases = caseRepository.findAll();
        for (Case acase : cases) {
            String caseKey = CASE_KEY_PREFIX + acase.getReference();
            redisTemplate.opsForValue().set(caseKey, acase.getId().toString());
        }
    }

    /**
     * Loads all users from the database into Redis.
    */
    @Transactional
    public void loadUserData() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            String userKey = USER_KEY_PREFIX + user.getEmail();
            redisTemplate.opsForValue().set(userKey, user.getId().toString());
        }
    }

    /**
     * Fetches and caches metadata for XML blobs from Azure Blob Storage using AzureBlobFetcher.
     *
     * @param containerName Azure Blob Storage container name.
     */
    public void fetchAndCacheXmlMetadata(String containerName) {
        Logger.getAnonymousLogger().info("PreProcessor: Fetching XML blobs from Azure Blob Storage...");
        int chunkSize = 1000; 
        int offset = 0;      
        List<String> blobNames;

        do {
            blobNames = azureBlobFetcher.fetchBlobNamesPaginated(containerName, offset, chunkSize);
            if (!blobNames.isEmpty()) {
                storeXmlResources(blobNames);
                Logger.getAnonymousLogger().info("Cached " + blobNames.size() + " blob names: " + blobNames);
            }
            offset += chunkSize;
        } while (!blobNames.isEmpty()); 

        Logger.getAnonymousLogger().info("PreProcessor: All XML blobs fetched and cached.");
    }


    /**
     * Stores XML resource references (blob names) in Redis for subsequent processing.
     *
     * @param xmlResources List of XML resource references to be stored.
     */
    public void storeXmlResources(List<String> xmlResources) {
        Logger.getAnonymousLogger().info("PreProcessor: Storing XML resource references...");
        redisTemplate.opsForValue().set(XML_RESOURCES_KEY, xmlResources);
        Logger.getAnonymousLogger().info("PreProcessor: XML resource references stored (" 
            + xmlResources.size() + " files).");
    }

    /**
     * Retrieves stored XML resource references (blob names) from Redis.
     *
     * @return List of XML resource references.
     */
    @SuppressWarnings("unchecked")
    public List<String> fetchXMLResourcesFromRedis() {
        Object value = redisTemplate.opsForValue().get("batch-preprocessor:xmlResources");

        if (value == null) {
            return List.of(); 
        }

        try {
            return (List<String>) value;
        } catch (ClassCastException e) {
            throw new IllegalStateException("Invalid data format for XML resources in Redis", e);
        }

    }


    public List<InputStreamResource> fetchAndProcessNextBatch(String containerName, int batchSize) {
        Logger.getAnonymousLogger().info("Fetching next batch of XML blobs for processing...");

        List<String> allBlobNames = fetchXMLResourcesFromRedis();
        if (allBlobNames.isEmpty()) {
            Logger.getAnonymousLogger().info("No more XML blobs left to process.");
            return List.of();
        }

        List<String> xmlBlobNames = allBlobNames.stream()
                                                .limit(batchSize)
                                                .toList();

        List<InputStreamResource> xmlFiles = new ArrayList<>();
        for (String blobName : xmlBlobNames) {
            Logger.getAnonymousLogger().info("Processing blob: " + blobName);
            InputStreamResource xmlFile = azureBlobFetcher.fetchSingleXmlBlob(containerName, blobName);
            if (xmlFile != null) {
                xmlFiles.add(xmlFile);
            } else {
                Logger.getAnonymousLogger().warning("Failed to fetch blob: " + blobName);
            }
        }

        allBlobNames.removeAll(xmlBlobNames);
        storeXmlResources(allBlobNames);

        Logger.getAnonymousLogger().info("Processed and removed " + xmlBlobNames.size() + " blob names from Redis.");
        return xmlFiles;
    }



}