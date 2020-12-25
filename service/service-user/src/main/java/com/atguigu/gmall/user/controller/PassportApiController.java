package com.atguigu.gmall.user.controller;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.IpUtil;
import com.atguigu.gmall.model.user.UserInfo;
import com.atguigu.gmall.user.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * @author luheng
 * @create 2020-12-16 20:50
 * @param:
 */
@RestController
@RequestMapping("/api/user/passport")
public class PassportApiController {

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 登录
     * @param userInfo
     * @param request
     * @param response
     * @return
     */
    @PostMapping("login")
    public Result login(@RequestBody UserInfo userInfo,
                        HttpServletRequest request,
                        HttpServletResponse response){

        UserInfo info = userService.login(userInfo);
        //判断
        if(info!=null){
        //token字符串 response.date.date  第二个date是一个对象或者map
            /**
             * 1.生成token并放入cookie
             * 2.将返回的对象放入cookie中
             * 3.后台需要存储originUrl，跳转页面使用
             * 4.如果cookie中有数据token。将数据放入请求头中
             */
            //制作一个token
            String token = UUID.randomUUID().toString();
            HashMap<String, Object> map = new HashMap<>();
            map.put("token",token);
            map.put("nickName",info.getNickName());
            //将userId和ip存储为一个json字符串
            JSONObject userJson = new JSONObject();

            userJson.put("userId",info.getId().toString());
            userJson.put("ip", IpUtil.getIpAddress(request));
            //将用户数据放入缓存中
            //定义key
            String userKey = RedisConst.USER_LOGIN_KEY_PREFIX + token;

            redisTemplate.opsForValue().set(userKey,userJson.toJSONString(),RedisConst.USERKEY_TIMEOUT, TimeUnit.SECONDS);


            //返回数据
            return Result.ok(map);
        }else {
            return Result.fail().message("登陆失败");
        }
    }

    /**
     * 退出登录
     * @param request
     * @return
     */
    @GetMapping("logout")
    public Result logout(HttpServletRequest request){
        //退出+清空缓存+cookie数据清空
        redisTemplate.delete(RedisConst.USER_LOGIN_KEY_PREFIX + request.getHeader("token"));
        return Result.ok();
    }
}
