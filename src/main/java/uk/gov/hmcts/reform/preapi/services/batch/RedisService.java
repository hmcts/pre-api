package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // =========================
    // Basic Redis Operations
    // =========================

    /**
     * Saves a value to Redis.
     * @param key The key under which the value is stored.
     * @param value The value to store.
     */
    public void saveValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * Gets a value from Redis.
     * @param key The key of the value to retrieve.
     * @param clazz The class type of the value.
     * @return The retrieved value, or null if the key does not exist.
     */
    public <T> T getValue(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return clazz.cast(value);
    }

    // =========================
    // Hash Operations
    // =========================

    /**
     * Checks if a field exists in a Redis hash.
     * @param key The key of the hash.
     * @param hashKey The field within the hash.
     * @return True if the field exists, false otherwise.
     */
    public boolean hashKeyExists(String key, String hashKey) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, hashKey));
    }

    /**
     * Saves a field to a Redis hash.
     * @param key The key of the hash.
     * @param hashKey The field in the hash.
     * @param value The value to store.
     */
    public void saveHashValue(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * Saves multiple key-value pairs to a Redis hash.
     * @param key The key of the Redis hash.
     * @param data A map containing the key-value pairs to be stored in the Redis hash.
     */
    public void saveHashAll(String key, Map<String, ?> data) {
        if (data != null && !data.isEmpty()) {
            redisTemplate.opsForHash().putAll(key, data);
        }
    }

    /**
     * Gets a value from a Redis hash.
     * @param key The key of the hash.
     * @param hashKey The field in the hash.
     * @param clazz The class type of the value.
     * @return The retrieved value, or null if the key or field does not exist.
     */
    public <T> T getHashValue(String key, String hashKey, Class<T> clazz) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        return clazz.cast(value);
    }

    /**
     * Retrieves all key-value pairs from a Redis hash.
     * @param key The key of the Redis hash .
     * @param keyType The class type of the keys in the hash.
     * @param valueType The class type of the values in the hash.
     * @return A map containing all the field-value pairs from the Redis hash, with keys and values.
     */
    public <K, V> Map<K, V> getHashAll(String key, Class<K> keyType, Class<V> valueType) {
        Map<Object, Object> redisMap = redisTemplate.opsForHash().entries(key);
        Map<K, V> result = new HashMap<>();

        for (Map.Entry<Object, Object> entry : redisMap.entrySet()) {
            K redisKey = keyType.cast(entry.getKey());
            V redisValue = valueType.cast(entry.getValue());
            result.put(redisKey, redisValue);
        }
        return result;
    }

    // =========================
    // Custom Key Generation
    // =========================

    /**
     * Generates a base key for case participants. Used to store recording metadaa.
     * @param caseReference The case reference.
     * @param participantPair The participant pair.
     * @return The generated base key.
     * @throws IllegalArgumentException If the case reference is null or blank.
     */
    public String generateBaseKey(String caseReference, String participantPair) {
        if (caseReference == null || caseReference.isBlank()) {
            throw new IllegalArgumentException("Case reference cannot be null or blank");
        }
        return "caseParticipants:" + caseReference + "--" + participantPair;
    }

}
