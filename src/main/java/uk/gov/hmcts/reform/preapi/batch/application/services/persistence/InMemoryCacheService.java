package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.ReportCsvWriter;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class InMemoryCacheService {
    private final LoggingService loggingService;

    private final Map<String, Map<String, Object>> hashStore = new ConcurrentHashMap<>();

    private final Map<String, CourtDTO> courtCache = new ConcurrentHashMap<>();
    private final Map<String, CaseDTO> caseCache = new ConcurrentHashMap<>();
    private final Map<String, BookingDTO> bookingCache = new ConcurrentHashMap<>();
    private final Map<String, CreateShareBookingDTO> shareBookingCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> userCache = new ConcurrentHashMap<>();

    private final Map<String, String> siteReferenceCache = new ConcurrentHashMap<>();
    private final Map<String, List<String[]>> channelReferenceCache = new ConcurrentHashMap<>();

    @Autowired
    public InMemoryCacheService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }


    // -----------------------------
    // Courts
    // -----------------------------
    public void saveCourt(String courtName, CourtDTO courtDTO) {
        courtCache.put(courtName, courtDTO);
    }

    public Optional<CourtDTO> getCourt(String courtName) {
        return Optional.ofNullable(courtCache.get(courtName));
    }

    // -----------------------------
    // Cases
    // -----------------------------
    public void saveCase(String caseRef, CaseDTO caseDTO) {
        caseCache.put(caseRef, caseDTO);
    }

    public Optional<CaseDTO> getCase(String caseRef) {
        return Optional.ofNullable(caseCache.get(caseRef));
    }

    // -----------------------------
    // Bookings
    // -----------------------------
    public void saveBooking(String bookingId, BookingDTO bookingDTO) {
        bookingCache.put(bookingId, bookingDTO);
    }

    public Optional<BookingDTO> getBooking(String bookingId) {
        return Optional.ofNullable(bookingCache.get(bookingId));
    }

    // -----------------------------
    // Share Bookings
    // -----------------------------
    public void saveShareBooking(String cacheKey, CreateShareBookingDTO dto) {
        shareBookingCache.put(cacheKey, dto);
    }

    public Optional<CreateShareBookingDTO> getShareBooking(String cacheKey) {
        return Optional.ofNullable(shareBookingCache.get(cacheKey));
    }

    // -----------------------------
    // Users
    // -----------------------------
    public void saveUser(String email, UUID userID) {
        userCache.put(email.toLowerCase(), userID);
    }

    public Optional<UUID> getUser(String email) {
        return Optional.ofNullable(userCache.get(email.toLowerCase()));
    }

    // -----------------------------
    // Reference Data
    // -----------------------------
    public void saveSiteReference(String siteRef, String courtName) {
        siteReferenceCache.put(siteRef, courtName);
    }

    public Optional<String> getSiteReference(String siteRef) {
        return Optional.ofNullable(siteReferenceCache.get(siteRef));
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


    // -----------------------------
    // Shared "HashStore" for Flexible Metadata
    // -----------------------------
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

    public  String generateCacheKey(String namespace, String type, String... identifiers) {
        return "vf:" + namespace + ":" + type + ":" + String.join("-", normalizeAll(identifiers));
    }

    private static List<String> normalizeAll(String[] parts) {
        return Arrays.stream(parts)
            .filter(Objects::nonNull)
            .map(String::trim)
            .map(String::toLowerCase)
            .toList();
    }

    // -----------------------------
    // Dump to CSV for Debug
    // -----------------------------
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
        for (Map.Entry<String, CaseDTO> entry : caseCache.entrySet()) {
            rows.add(List.of("db:cases", entry.getKey(), entry.getValue().toString()));
        }

        // caseCache
        for (Map.Entry<String, UUID> entry : userCache.entrySet()) {
            rows.add(List.of("db:users", entry.getKey(), entry.getValue().toString()));
        }

        // shareBookingCache
        for (Map.Entry<String, CreateShareBookingDTO> entry : shareBookingCache.entrySet()) {
            rows.add(List.of(entry.getKey(), entry.getValue().toString()));
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
