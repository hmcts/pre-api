package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    @Autowired
    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void saveValue(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public <T> T getValue(String key, Class<T> clazz) {
        Object value = redisTemplate.opsForValue().get(key);
        return clazz.cast(value);
    }

    public void saveHashValue(String key, String hashKey, Object value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    public <T> T getHashValue(String key, String hashKey, Class<T> clazz) {
        Object value = redisTemplate.opsForHash().get(key, hashKey);
        return clazz.cast(value);
    }

    public boolean hashKeyExists(String key, String hashKey) {
        return Boolean.TRUE.equals(redisTemplate.opsForHash().hasKey(key, hashKey));
    }

    public String generateBaseKey(String caseReference, String participantPair) {
        if (caseReference == null || caseReference.isBlank()) {
            throw new IllegalArgumentException("Case reference cannot be null or blank");
        }
        return "caseParticipants:" + caseReference + "--" + participantPair;
    }

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

    public Set<String> getKeys(String pattern) {
        return redisTemplate.keys(pattern);
    }

    public void generateHashKeys(String baseKey, String participantPair) {
        Map<String, String> hashKeys = new HashMap<>();
        saveHashValue(baseKey, "participantPairField", participantPair);
        hashKeys.put("participantPairField", participantPair);
        hashKeys.put("bookingField", "booking");
        hashKeys.put("captureSessionField", "captureSession");
        hashKeys.put("highestOrigVersion", "ORIG");
        hashKeys.put("highestCopyVersion", "COPY");
    }

    public void removeValue(String key) {
        redisTemplate.delete(key);
    }

    public void removeHashValue(String key, String hashKey) {
        redisTemplate.opsForHash().delete(key, hashKey);
    }

    


}
