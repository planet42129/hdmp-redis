package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private CacheClient cacheClient;

    @Test
    void testSaveShop() throws InterruptedException {
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



}
