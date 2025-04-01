package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportingService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryCacheService {
    private final ReportingService reportingService;
    private final LoggingService loggingService;
    private final Map<String, Map<String, Object>> hashStore = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryCacheService(ReportingService reportingService, LoggingService loggingService) {
        this.reportingService = reportingService;
        this.loggingService = loggingService;
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

    public Map<String, Object> getHashAll(String key) {
        return hashStore.getOrDefault(key, null);
    }

    public void clearNamespaceKeys(String namespacePrefix) {
        Set<String> keys = new HashSet<>(hashStore.keySet());
        for (String key : keys) {
            if (key.startsWith(namespacePrefix)) {
                hashStore.remove(key);
            }
        }
    }

    public String generateBaseKey(String caseReference, String participantPair) {
        if (caseReference == null || caseReference.isBlank()) {
            throw new IllegalArgumentException("Case reference cannot be null or blank");
        }
        return "vf:case:" + caseReference + ":participants:" + participantPair;
    }

    public void dumpToFile() {
        List<String> headers = List.of("Key", "Field", "Value");
        List<List<String>> rows = new ArrayList<>();

        for (Map.Entry<String, Map<String, Object>> entry : hashStore.entrySet()) {
            String key = entry.getKey();
            for (Map.Entry<String, Object> inner : entry.getValue().entrySet()) {
                rows.add(List.of(key, inner.getKey(), String.valueOf(inner.getValue())));
            }
        }

        try {
            reportingService.writeToCsv(headers, rows, "in-memory-cache", "Migration Reports", false);
        } catch (IOException e) {
            loggingService.logError("Failed to write in-memory cache to file: " + e.getMessage());
        }
    }

    public String getAsString(String key, String hashKey) {
        Object value = getHashValue(key, hashKey, Object.class);
        return (value instanceof String stringVal) ? stringVal : null;
    }

    @SuppressWarnings("unchecked")
    public List<String[]> getAsStringArrayList(String key, String hashKey) {
        Object value = getHashValue(key, hashKey, Object.class);
        if (value instanceof List<?>) {
            try {
                return (List<String[]>) value;
            } catch (ClassCastException e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("unchecked")
    public <T> T getAllAsType(String key, Class<T> clazz) {
        Object value = getHashAll(key);
        return clazz.isInstance(value) ? (T) value : null;
    }
}
