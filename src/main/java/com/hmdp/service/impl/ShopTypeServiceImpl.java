package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String key = "cache:shop-type:"  + "list";

        //1、从redis中查询缓存
        String shopJsonList = stringRedisTemplate.opsForValue().get(key);
        //2、判断是否存在
        if (StrUtil.isNotBlank(shopJsonList)) {
            //3、存在，则直接返回
            List<ShopType> shopList = JSONUtil.toList(shopJsonList, ShopType.class);
            return Result.ok(shopList);
        }

        //4、不存在，向mysql查询
        List<ShopType> shopList = query().orderByAsc("sort").list();
        //5、不存在，返回错误 404
        if (shopList == null) {
            Result.fail("店铺列表不存在");
        }

        //6、存在，存入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shopList), 30 + RandomUtil.randomInt(10), TimeUnit.MINUTES);

        //7、返回
        return Result.ok(shopList);
    }
}
