package com.mdt.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class DistributedLock {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "mdt:lock:";

    private static final RedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "return redis.call('del', KEYS[1]) " +
                    "else return 0 end",
            Long.class
    );

    private final ThreadLocal<String> lockHolder = new ThreadLocal<>();

    public boolean tryLock(String lockKey, long waitTimeMs, long leaseTimeMs) {
        String key = LOCK_PREFIX + lockKey;
        String value = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        long deadline = startTime + waitTimeMs;

        try {
            while (System.currentTimeMillis() < deadline) {
                Boolean acquired = redisTemplate.opsForValue()
                        .setIfAbsent(key, value, leaseTimeMs, TimeUnit.MILLISECONDS);

                if (Boolean.TRUE.equals(acquired)) {
                    lockHolder.set(value);
                    log.debug("获取锁成功: key={}, value={}", key, value);
                    return true;
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("获取分布式锁异常, key={}", key, e);
        }

        log.warn("获取锁超时: key={}, waitTime={}ms", key, waitTimeMs);
        return false;
    }

    public void lock(String lockKey, long leaseTimeMs) {
        boolean acquired = tryLock(lockKey, 3000, leaseTimeMs);
        if (!acquired) {
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
    }

    public void unlock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        String value = lockHolder.get();

        if (value == null) {
            log.warn("解锁失败: 当前线程未持有锁, key={}", key);
            return;
        }

        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), value);
            if (Long.valueOf(1).equals(result)) {
                log.debug("释放锁成功: key={}", key);
            } else {
                log.warn("释放锁失败: 值不匹配或锁已过期, key={}", key);
            }
        } catch (Exception e) {
            log.error("释放分布式锁异常, key={}", key, e);
        } finally {
            lockHolder.remove();
        }
    }

    public <T> T executeWithLock(String lockKey, long leaseTimeMs, LockCallback<T> callback) {
        lock(lockKey, leaseTimeMs);
        try {
            return callback.execute();
        } finally {
            unlock(lockKey);
        }
    }

    public void executeWithLock(String lockKey, long leaseTimeMs, Runnable action) {
        lock(lockKey, leaseTimeMs);
        try {
            action.run();
        } finally {
            unlock(lockKey);
        }
    }

    @FunctionalInterface
    public interface LockCallback<T> {
        T execute();
    }
}
