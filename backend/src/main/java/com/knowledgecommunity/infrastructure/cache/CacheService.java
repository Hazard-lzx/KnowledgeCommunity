package com.knowledgecommunity.infrastructure.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * 缓存服务：封装 Redis 常用操作
 * 提供统一的缓存读写接口，简化业务代码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;

    /** 获取缓存值 */
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /** 设置缓存（带过期时间） */
    public void set(String key, String value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    /** 设置缓存（仅当 key 不存在时），用于分布式锁 */
    public Boolean setIfAbsent(String key, String value, long timeout, TimeUnit unit) {
        return redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit);
    }

    /** 删除缓存 */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** 判断缓存是否存在 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }

    /** 设置过期时间 */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /** 自增1 */
    public Long increment(String key) {
        return redisTemplate.opsForValue().increment(key);
    }

    /** 自增指定值 */
    public Long increment(String key, long delta) {
        return redisTemplate.opsForValue().increment(key, delta);
    }
}
