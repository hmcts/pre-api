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

    public void generateHashKeys(String baseKey, String participantPair) {
        Map<String, String> hashKeys = new HashMap<>();
        saveHashValue(baseKey, "participantPairField", participantPair);
        hashKeys.put("participantPairField", participantPair);
        hashKeys.put("bookingField", "booking");
        hashKeys.put("captureSessionField", "captureSession");
        hashKeys.put("highestOrigVersion", "ORIG");
        hashKeys.put("highestCopyVersion", "COPY");
    }



}
