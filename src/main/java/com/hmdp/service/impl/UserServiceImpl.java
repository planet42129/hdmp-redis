package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1、校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2、如果不符合，返回
            return Result.fail("手机号不正确");
        }
        //3、符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //todo 感觉应该把手机号和验证码一起保存到session 因为可能出现A手机号申请发送的验证码，B手机号登录
        //4、保存验证码到session
        session.setAttribute("code", code);
        //5、发送验证码
        log.debug("发送短信验证码成功，验证码：{}", code);
        //6、返回ok
        return Result.ok();
    }


    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1、校验手机号是否合法
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式不正确");
        }

        //2、校验验证码
        //todo code 常量 放到一个统一的地方
        Object cacheCode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.toString().equals(code)) {
            //3、不一致，报错
            return Result.fail("验证码错误");
        }
        //3、一致，根据手机号查询用户 select * from tb_user where phone = #
        User user = query().eq("phone", phone).one();

        //4、用户是否存在
        if (user == null) {
            //5、不存在，创建新用户，保存到数据库
            user = createUserWithPhone(phone);
        }
        //存 User user到session，信息越完整，用起来越方便
        //但是 session是存储在服务器中的，信息太多，服务器压力大
        //并且有些用户敏感信息不要直接返回
        //返回基本信息即可

        //笨方法
       /* UserDTO userDTO = new UserDTO();
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());
        userDTO.setId(user.getId());
        session.setAttribute("user", userDTO);*/
        //改用hutools里的BeanUtil的copyProperties
        //6、保存用户到session
        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        //1、新建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        //2、保存用户
        save(user);
        return user;
    }
}
