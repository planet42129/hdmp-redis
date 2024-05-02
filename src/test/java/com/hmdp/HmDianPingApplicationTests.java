package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));

    }

    @Test
    void testSaveShopToRedis() throws InterruptedException {
//        shopService.saveShop2Redis(1L, 10L);


        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


    @Test
    void testUserMapFieldIsString() {
        UserDTO userDTO = new UserDTO();
        userDTO.setId(0L);
        userDTO.setNickName("1");
        userDTO.setIcon("111");
        /*Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        //把bean里边的字段的值转换为String，也可以不用这个 BeanUtil.beanToMap()，自己手动转
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));*/
        Map<String, Object> map = new HashMap<>();
        map.put("id", userDTO.getId().toString());
        map.put("nickName", userDTO.getNickName());
        map.put("icon", userDTO.getIcon());
        System.out.println(map);
    }

    @Test
    void testIdIncrement() {
        LocalDateTime now = LocalDateTime.now();
        long BEGIN_TIMESTAMP = LocalDateTime.of(2022,1,1,0,0,0)
                .toEpochSecond(ZoneOffset.UTC);
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;
        System.out.println("timestamp = " + timestamp);
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + "test" + ":" + date);
        System.out.println("count = " + count);

        System.out.println(timestamp << 32 | count);
    }




}
