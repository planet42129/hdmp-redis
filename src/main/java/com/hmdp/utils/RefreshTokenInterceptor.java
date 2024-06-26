package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @author hyh
 * @date 2024/4/10
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    //这里不能使用@Resource、@Autowired注解来注入redis对象，需要使用构造函数
    //因为这个类的对象是我们手动new出来的（在MvcConfig），不是通过Component等注解创建的，也就是不是由Spring创建的
    //也可以在MvcConfig中通过注解注入的方式注入拦截器
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1、获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //放行
            return true;
        }

        String tokenKey = LOGIN_USER_KEY + token;

        //2、基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(tokenKey);
        //3、判断用户是否存在
        if (userMap.isEmpty()) {
            //放行
            return true;
        }

        // 5、将查询到的HashMap数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //6、存在，保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        //7、刷新token的有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8、放行
        return true;
    }

    //在渲染完成之后
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
