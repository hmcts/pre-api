package uk.gov.hmcts.reform.preapi.services.batch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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

}
