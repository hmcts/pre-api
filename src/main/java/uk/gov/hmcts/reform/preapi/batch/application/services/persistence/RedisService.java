package uk.gov.hmcts.reform.preapi.batch.application.services.persistence;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.preapi.batch.application.services.reporting.LoggingService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class RedisService {
    private LoggingService loggingService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${redis.default.ttl:86400}")
    private long defaultTtl;

    @Value("${redis.retry.maxAttempts:3}")
    private int maxRetryAttempts;

    @Autowired
    public RedisService(
        RedisTemplate<String, Object> redisTemplate,
        LoggingService loggingService
    ) {
        this.redisTemplate = redisTemplate;
        this.loggingService = loggingService;
    }

    // =========================
    // Basic Redis Operations
    // =========================

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveValue(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            loggingService.logError("Error saving value for key: %s - %s", key, e);
            throw e;
        }
    }

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public <T> T getValue(String key, Class<T> clazz) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value != null) {
                redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
            } 
            
            return value != null ? clazz.cast(value) : null;
        } catch (Exception e) {
            loggingService.logError("Error getting value for key: %s - %s", key, e);
            throw e;
        }
       
    }

    // =========================
    // Hash Operations
    // =========================

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public boolean checkHashKeyExists(String key, String hashKey) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, hashKey));
        } catch (Exception e) {
            loggingService.logError("Error checking if hash key exists: %s:%s - %s", key, hashKey, e);
            throw e;
        }
    }

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveHashValue(String key, String hashKey, Object value) {
        try {
            redisTemplate.opsForHash().put(key, hashKey, value);
            redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            loggingService.logError("Error saving hash value for %s:%s - %s", key, hashKey, e);
            throw e;
        }
    }

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void saveHashAll(String key, Map<String, ?> data) {
        try {
            if (data == null) {
                return;
            }

            redisTemplate.opsForHash().putAll(key, data);
            redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
        } catch (Exception e) {
            loggingService.logError("Error saving hash entries to key: %s - %s", key, e);
            throw e;
        }
        
    }

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public <T> T getHashValue(String key, String hashKey, Class<T> clazz) {
        try { 
            Object value = redisTemplate.opsForHash().get(key, hashKey);
            if (value != null) {
                redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
                return clazz.cast(value);
            } else {
                return null;
            }
        } catch (Exception e) {
            loggingService.logError("Error getting hash value for %s:%s", key, hashKey, e);
            throw e;
        } 
    }

    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public <K, V> Map<K, V> getHashAll(String key, Class<K> keyType, Class<V> valueType) {
        try {
            Map<Object, Object> redisMap = redisTemplate.opsForHash().entries(key);
            Map<K, V> result = new HashMap<>();

            if (redisMap.isEmpty()) {
                loggingService.logDebug("No hash entries found for key: %s", key);
                return result;
            }

            for (Map.Entry<Object, Object> entry : redisMap.entrySet()) {
                K redisKey = keyType.cast(entry.getKey());
                V redisValue = valueType.cast(entry.getValue());
                result.put(redisKey, redisValue);
            }

            redisTemplate.expire(key, defaultTtl, TimeUnit.SECONDS);
            return result;
        } catch (Exception e) {
            loggingService.logError("Error retrieving all hash entries for key: %s - %s", key, e);
            throw e;
        }
    }


    @Retryable(
        value = {RedisConnectionFailureException.class, DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public void clearNamespaceKeys(String namespace) {
        try {
            Set<String> keys = redisTemplate.keys(namespace + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            loggingService.logError("Error clearing keys with namespace: %s - %s", namespace, e);
            throw e;
        }
        
    }

    // =========================
    // Custom Key Generation
    // =========================

    public String generateBaseKey(String caseReference, String participantPair) {
        if (caseReference == null || caseReference.isBlank()) {
            throw new IllegalArgumentException("Case reference cannot be null or blank");
        }
        return "vf:case:" + caseReference + ":participants:" + participantPair;
    }

}
