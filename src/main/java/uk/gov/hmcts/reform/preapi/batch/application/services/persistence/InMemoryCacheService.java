package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.batch.config.Constants;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@SuppressWarnings("PMD.CouplingBetweenObjects")
public class InMemoryCacheService {
    private final LoggingService loggingService;

    private final Map<String, Map<String, Object>> hashStore = new ConcurrentHashMap<>();

    private final Map<String, CourtDTO> courtCache = new ConcurrentHashMap<>();
    private final Map<String, CreateCaseDTO> caseCache = new ConcurrentHashMap<>();
    private final Map<String, CreateShareBookingDTO> shareBookingCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> userCache = new ConcurrentHashMap<>();

    private final Map<String, String> siteReferenceCache = new ConcurrentHashMap<>();
    private final Map<String, List<String[]>> channelReferenceCache = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryCacheService(final LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void saveCourt(String courtName, CourtDTO courtDTO) {
        courtCache.put(courtName, courtDTO);
    }

    public Optional<CourtDTO> getCourt(String courtName) {
        return Optional.ofNullable(courtCache.get(courtName));
    }

    public Optional<CreateCaseDTO> getCase(String caseRef) {
        if (caseRef == null || caseRef.isBlank()) {
            loggingService.logDebug("Attempted to get case with null or empty reference");
            return Optional.empty();
        }

        String normalizedRef = normalizeCaseReference(caseRef);
        loggingService.logInfo("Getting case from cache - Original: '%s', Normalized: '%s'", caseRef, normalizedRef);
        CreateCaseDTO result = caseCache.get(normalizedRef);
        if (result != null) {
            loggingService.logInfo("Found case in cache: %s", result.getId());
        } else {
            loggingService.logInfo("Case not found in cache for reference: '%s'", normalizedRef);
        }


        return Optional.ofNullable(result);
    }

    public void saveCase(String caseRef, CreateCaseDTO caseDTO) {
        if (caseRef == null || caseRef.isBlank()) {
            loggingService.logInfo("Case ref is null or empty: %s", caseRef);
            return;
        }
        if (caseDTO == null) {
            loggingService.logInfo("CaseDTO is null");
            return;
        }

        String normalizedRef = normalizeCaseReference(caseRef);
        loggingService.logInfo("Saving case to cache - Original: '%s', Normalized: '%s', Case ID: %s",
                          caseRef, normalizedRef, caseDTO.getId());
        caseCache.put(normalizedRef, caseDTO);
    }


    private String normalizeCaseReference(String caseRef) {
        if (caseRef == null) {
            return null;
        }
        return caseRef.trim().toUpperCase(Locale.UK).replaceAll("\\s+", " ");
    }

    public void saveShareBooking(String cacheKey, CreateShareBookingDTO dto) {
        shareBookingCache.put(cacheKey, dto);
    }

    public Optional<CreateShareBookingDTO> getShareBooking(String cacheKey) {
        return Optional.ofNullable(shareBookingCache.get(cacheKey));
    }

    public void saveUser(String email, UUID userID) {
        if (email == null || email.isBlank() || userID == null) {
            loggingService.logWarning("Skipping saveUser: email or userID missing");
            return;
        }
        String lowerEmail = email.toLowerCase(Locale.UK);
        userCache.put(lowerEmail, userID);
        saveHashValue(Constants.CacheKeys.USERS_PREFIX, lowerEmail, userID.toString());
    }

    public void saveSiteReference(String siteRef, String courtName) {
        siteReferenceCache.put(siteRef, courtName);
    }

    public Map<String, String> getAllSiteReferences() {
        return new HashMap<>(siteReferenceCache);
    }

    public void saveChannelReference(String channelName, List<String[]> users) {
        channelReferenceCache.put(channelName, users);
    }

    public Optional<List<String[]>> getChannelReference(String channelName) {
        return Optional.ofNullable(channelReferenceCache.get(channelName));
    }

    public Map<String, List<String[]>> getAllChannelReferences() {
        return new HashMap<>(channelReferenceCache);
    }

    public void saveHashAll(String key, Map<String, Object> data) {
        hashStore.put(key, new ConcurrentHashMap<>(data));
    }

    public void saveHashValue(String key, String hashKey, Object value) {
        hashStore.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(hashKey, value);
    }

    public boolean checkHashKeyExists(String key, String hashKey) {
        return hashStore.containsKey(key) && hashStore.get(key).containsKey(hashKey);
    }

    @SuppressWarnings("unchecked")
    public <T> T getHashValue(String key, String hashKey, Class<T> clazz) {
        Object value = hashStore.containsKey(key) ? hashStore.get(key).get(hashKey) : null;
        return clazz.isInstance(value) ? (T) value : null;
    }

    public void clearNamespaceKeys(String namespacePrefix) {
        Set<String> keys = new HashSet<>(hashStore.keySet());
        for (String key : keys) {
            if (key.startsWith(namespacePrefix)) {
                hashStore.remove(key);
            }
        }
    }

    public String generateEntityCacheKey(String entityType, String... parts) {
        return String.format("vf:%s:%s", entityType, String.join("-", normalizeAll(parts)));
    }

    private static List<String> normalizeAll(String... parts) {
        return Arrays.stream(parts)
            .filter(Objects::nonNull)
            .map(String::toLowerCase)
            .toList();
    }

    public void dumpToFile() {
        List<String> headers = List.of("Key", "Field", "Value");
        List<List<String>> rows = new ArrayList<>();

        // hashStore
        for (Map.Entry<String, Map<String, Object>> entry : hashStore.entrySet()) {
            String key = entry.getKey();
            for (Map.Entry<String, Object> inner : entry.getValue().entrySet()) {
                rows.add(List.of(key, inner.getKey(), String.valueOf(inner.getValue())));
            }
        }

        //  courtCache
        for (Map.Entry<String, CourtDTO> entry : courtCache.entrySet()) {
            rows.add(List.of("db:courts", entry.getKey(), entry.getValue().toString()));
        }

        //  caseCache
        for (Map.Entry<String, CreateCaseDTO> entry : caseCache.entrySet()) {
            rows.add(List.of("db:cases", entry.getKey(), entry.getValue().toString()));
        }

        // userCache
        for (Map.Entry<String, UUID> entry : userCache.entrySet()) {
            rows.add(List.of("db:users", entry.getKey(), entry.getValue().toString()));
        }

        // shareBookingCache
        for (Map.Entry<String, CreateShareBookingDTO> entry : shareBookingCache.entrySet()) {
            rows.add(List.of("db:sharebookings", entry.getKey(), entry.getValue().toString()));
        }

        // //  siteReferenceCache
        for (Map.Entry<String, String> entry : siteReferenceCache.entrySet()) {
            rows.add(List.of("ref:sites", entry.getKey(), entry.getValue()));
        }

        // channelReferenceCache
        for (Map.Entry<String, List<String[]>> entry : channelReferenceCache.entrySet()) {
            String readable = entry.getValue().stream()
                .map(pair -> String.join(" ", pair[0], "<" + pair[1] + ">"))
                .toList()
                .toString();
            rows.add(List.of("ref:channels", entry.getKey(), readable));
        }

        try {
            ReportCsvWriter.writeToCsv(headers, rows, "in-memory-cache", "Migration Reports", false);
        } catch (IOException e) {
            loggingService.logError("Failed to write in-memory cache to file: " + e.getMessage());
        }
    }
}
