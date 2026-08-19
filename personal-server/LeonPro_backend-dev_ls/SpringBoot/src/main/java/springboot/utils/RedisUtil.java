package springboot.utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.TimeUnit;
@Service
public class RedisUtil {
    private static final Logger logger = LoggerFactory.getLogger(RedisUtil.class);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    /**
     * 存储数据（无过期时间）
     *
     * @param key   键
     * @param value 值
     */
    public void save(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, value);
            logger.info("Redis 存储成功 - Key: {}, Value: {}", key, value);
        } catch (Exception e) {
            logger.error("Redis 存储失败 - Key: {}, Value: {}", key, value, e);
            throw new RuntimeException("Redis 存储失败", e);
        }
    }
    /**
     * 存储数据（支持过期时间）
     *
     * @param key      键
     * @param value    值
     * @param timeout  过期时间
     * @param timeUnit 时间单位
     */
    public void save(String key, Object value, long timeout, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
            logger.info("Redis 存储成功 - Key: {}, Value: {}, Timeout: {} {}", key, value, timeout, timeUnit);
        } catch (Exception e) {
            logger.error("Redis 存储失败 - Key: {}, Value: {}, Timeout: {} {}", key, value, timeout, timeUnit, e);
            throw new RuntimeException("Redis 存储失败", e);
        }
    }
    /**
     * 获取数据
     *
     * @param key 键
     * @return 值
     */
    public <T> T get(String key) {
        try {
            T value = (T) redisTemplate.opsForValue().get(key);
            logger.info("Redis 获取成功 - Key: {}, Value: {}", key, value);
            return value;
        } catch (Exception e) {
            logger.error("Redis 获取失败 - Key: {}", key, e);
            throw new RuntimeException("Redis 获取失败", e);
        }
    }
    /**
     * 删除数据
     *
     * @param key 键
     */
    public void delete(String key) {
        try {
            redisTemplate.delete(key);
            logger.info("Redis 删除成功 - Key: {}", key);
        } catch (Exception e) {
            logger.error("Redis 删除失败 - Key: {}", key, e);
            throw new RuntimeException("Redis 删除失败", e);
        }
    }
}
