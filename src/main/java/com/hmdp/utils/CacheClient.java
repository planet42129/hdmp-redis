package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @author hyh
 * @date 2024/4/13
 */
@Slf4j
@Component
public class CacheClient {
    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 设置普通缓存数据
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, timeUnit);
    }

    /**
     * 设置逻辑过期类型的缓存数据
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 解决缓存穿透
     *
     * @param keyPrefix
     * @param id
     * @param type
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //1、从redis中查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //3、存在，直接返回
            //反序列化
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否是空字符串""  不等于null，则说明是之前缓存的""
        if (json != null) {
            return null;
        }

        //4、不存在，根据id查询数据库
        R r = dbFallback.apply(id);

        //5、商铺不存在，返回错误 404
        if (r == null) {
            //将空字符串写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6、存在，写入redis
        this.set(key, r, time, timeUnit);

        //7、返回商铺信息
        return r;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 逻辑过期解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time, TimeUnit timeUnit) {
        String key = keyPrefix + id;
        //1、从redis中查询缓存数据
        String json = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if (StrUtil.isBlank(json)) {
            //3、不存在，直接返回
            return null;
        }

        // 4 命中，需要先把json反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
//        JSONObject data = (JSONObject) redisData.getData();
//        Shop shop = JSONUtil.toBean(data, type);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回缓存数据
            return r;
        }

        //5.2 已过期，需要缓存重建
        //6 缓存重建
        // 6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        // 6.2 判断是否获取锁成功
        if (isLock) {
            // todo 获取锁成功应该再次检测redis缓存是否过期，做doubleCheck
            // 6.3 成功，开启独立线程实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查数据库
                    R r1 = dbFallback.apply(id);
                    //写redis
                    this.setWithLogicalExpire(key, r1, time, timeUnit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }

        //6.4 返回过期的旧数据（不管获取锁成功失败与否）
        return r;
    }

    private boolean tryLock(String key) {
        //设置的有效期比实际业务的执行时间长一点
        Boolean flag = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return flag;  //不要直接返回flag，会执行自动拆箱，可能会有空指针
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

}
