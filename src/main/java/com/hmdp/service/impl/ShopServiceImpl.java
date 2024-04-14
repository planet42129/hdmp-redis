package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
//                id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //在这里 id2 -> getById(id2) 等价于 this::getById
//        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class,
//                this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);



        //互斥锁解决缓存击穿
//        Shop shop = queryWithMutex(id);

        //逻辑过期解决缓存击穿
//        Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class,
                id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.SECONDS);//改成秒 方便测试
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }


    /**
     * 缓存穿透
     * @param id
     * @return
     */
    public Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1、从redis中查询商铺缓存
        //同样可以使用Hash，为了练习多种方式（前面已经使用过Hash），这里使用String
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //3、存在，直接返回
            //反序列化
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否是空字符串""  不等于null，则说明是之前缓存的""
        if (shopJson != null) {
            log.info("redis中查找：" + shopJson);
            return null;
        }

        //4、不存在，根据id查询数据库
        Shop shop = getById(id);
        log.info("mysql中查找：" + shop);

        //5、商铺不存在，返回错误 404
        if (shop == null) {
            //将空字符串写入redis
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            //返回错误信息
            return null;
        }
        //6、存在，写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);

        //7、返回商铺信息
        return shop;
    }


    /**
     * 缓存击穿：
     * 也叫热点key问题，产生原因：1 高并发访问的key； 2 缓存重建过程耗时较长
     * 3 热点key突然过期，因为重建耗时长，在这段时间内大量请求落到数据库，带来巨大冲击
     * 解决方案1：互斥锁
     * 解决方案2：逻辑过期
     *
     * @param id
     * @return
     */
    public Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            //3、存在，直接返回
            //反序列化
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否是空字符串""  不等于null，则说明是之前缓存的""
        if (shopJson != null) {
            return null;
        }
        //4、不存在，实现缓存重建
        //4.1 尝试获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean isLock = tryLock(lockKey);
            //4.2 判断是否成功获取锁
            if (isLock != true) {
                //4.3 如果失败，休眠并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 获取互斥锁成功
            // todo 注意：获取锁成功应该再次检查redis缓存是否存在，做DoubleCheck，如果存在则无需建立缓存
            shopJson = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson)) {
                //3、存在，直接返回
                //反序列化
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //判断命中的是否是空字符串""  不等于null，则说明是之前缓存的""
            if (shopJson != null) { //即是""
                return null;
            }

            //模拟重建延时
            Thread.sleep(200);

            // 根据id查询数据库
            shop = getById(id);
            //5、商铺不存在，返回错误 404
            if (shop == null) {
                //将空字符串写入redis
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //6、存在，写入redis
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(10), TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7、释放互斥锁
            unlock(lockKey);
        }
        //8、 返回商铺信息
        return shop;
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    /**
     * 缓存击穿：
     * 也叫热点key问题，产生原因：1 高并发访问的key； 2 缓存重建过程耗时较长
     * 3 热点key突然过期，因为重建耗时长，在这段时间内大量请求落到数据库，带来巨大冲击
     * 解决方案1：互斥锁
     * 解决方案2：逻辑过期
     * @param id
     * @return
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //1、从redis中查询商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2、判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //3、不存在，直接返回
            return null;
        }

        // 4 命中，需要先把json反序列化
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(data, Shop.class);
//        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 5.1 未过期，直接返回店铺信息
            return shop;
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
                    //重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }

        //6.4 返回过期的商铺信息（不管获取锁成功失败与否）
        return shop;
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

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        // 1 查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);

        // 2 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));

        // 3 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1、更新数据库
        updateById(shop);
        //2、删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        // 上面是单体系统的做法
        // 如果是分布式系统，
        // 例如更新数据库是服务器1完成，更新缓存可能是服务器2完成，这时可能需要消息队列去通知2
        // TTC方案
        return Result.ok();
    }
}
