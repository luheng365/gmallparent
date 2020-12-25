package com.atguigu.gmall.gateway.filter;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.IpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-17 11:07
 * @param:
 */
@Component
public class AuthGlobalFilter implements GlobalFilter {

    @Value("${authUrls.url}")
    private String authUrlsUrl;  // authUrlsUrl=trade.html,myOrder.html,list.html

    @Autowired
    private RedisTemplate redisTemplate;

    //  工具类
    private AntPathMatcher antPathMatcher = new AntPathMatcher();
    /**
     * 过滤器
     * @param exchange web请求的对象
     * @param chain 过滤器连
     * @return Mono<Void> 过滤器链的返回结果，
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        //  必须要获取用户现在访问的是哪个url
        ServerHttpRequest request = exchange.getRequest();
        //  http://list.gmall.com/list.html?category3Id=61
        String path = request.getURI().getPath();
        //  不允许访问内部调用接口
        //  path 判断path 中是否符合验证规则
        if (antPathMatcher.match("/**/inner/**",path)){
            //  符合条件
            //  设置一个响应
            ServerHttpResponse response = exchange.getResponse();
            //  提示没有权限  PERMISSION(209, "没有权限")
            return out(response, ResultCodeEnum.PERMISSION);
        }

        //  获取用户Id
        String userId = this.getUserId(request);
        String userTempId = this.getUserTempId(request);

        //  判断
        if ("-1".equals(userId)){
            ServerHttpResponse response = exchange.getResponse();
            return out(response,ResultCodeEnum.PERMISSION);
        }

        //  判断用户访问哪些控制器一定要登录  “trade.html”
        if (!StringUtils.isEmpty(authUrlsUrl)){
            //  authUrlsUrl = trade.html,myOrder.html,list.html
            //  分割
            String[] split = authUrlsUrl.split(",");
            if (split!=null && split.length>0){
                // 遍历
                for (String url : split) {
                    //  当前请求路径中包含上述的请求控制器 并且 用户未登录，则需要拦截
                    if (path.indexOf(url)!=-1 && StringUtils.isEmpty(userId)){
                        //  获取响应
                        ServerHttpResponse response = exchange.getResponse();
                        //  设置请求码
                        response.setStatusCode(HttpStatus.SEE_OTHER);
                        //  http://passport.gmall.com/login.html?originUrl=http://www.gmall.com/
                        //  HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI()
                        //  登录,添加请求头add
                        //  如果符合条件应该设置请求头 set
                        // response.getHeaders().add(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                        response.getHeaders().set(HttpHeaders.LOCATION,"http://www.gmall.com/login.html?originUrl="+request.getURI());
                        //  重定向到登录页面
                        return response.setComplete();

                    }
                }
            }
        }
        //  用户访问这样的 数据接口 /api/**/auth/** 这样的接口，则必须登录！
        //  http://localhost/api/product/auth/hello
        if (antPathMatcher.match("/api/**/auth/**",path)){
            if (StringUtils.isEmpty(userId)){
                //  必须登录
                //  设置一个响应
                ServerHttpResponse response = exchange.getResponse();
                //  提示没有权限  LOGIN_AUTH(208, "未登陆")
                return out(response, ResultCodeEnum.LOGIN_AUTH);
            }
        }

        //  将获取到的用户Id 传递到后台的微服务
        if (!StringUtils.isEmpty(userId) || !StringUtils.isEmpty(userTempId)){
            //  用户登录的情况
            if (!StringUtils.isEmpty(userId)){
                //  将用户Id 放入请求头中 ServerHttpRequest
                request.mutate().header("userId", userId).build();
            }
            //  用户未登录状态
            if (!StringUtils.isEmpty(userTempId)){
                request.mutate().header("userTempId", userTempId).build();
            }
            //  将 request 变为 exchange
            return chain.filter(exchange.mutate().request(request).build());
        }
        // Mono<Void>  chain.filter(exchange)
        return chain.filter(exchange);
    }

//    private String getUserTempId(ServerHttpRequest request) {
//        //  临时用户Id，可以存在请求头，或者是cookie 中
//        String userTempId = "";
//        List<String> list = request.getHeaders().get("userTempId");
//        if (!CollectionUtils.isEmpty(list)){
//            userTempId = list.get(0);
//        }else {
//            HttpCookie httpCookie = request.getCookies().getFirst("userTempId");
//            if (httpCookie!=null){
//                userTempId = httpCookie.getValue();
//            }
//        }
//        //  返回临时用户Id
//        return userTempId;
//    }
private String getUserTempId(ServerHttpRequest request) {
    //  临时用户Id，可以存在请求头，或者是cookie 中
    String userTempId = "";
    List<String> list = request.getHeaders().get("userTempId");
    if (!CollectionUtils.isEmpty(list)){
        userTempId = list.get(0);
    }else {
        HttpCookie httpCookie = request.getCookies().getFirst("userTempId");
        if (httpCookie!=null){
            userTempId = httpCookie.getValue();
        }
    }
    //  返回临时用户Id
    return userTempId;
}

    //  提示方法
    private Mono<Void> out(ServerHttpResponse response, ResultCodeEnum resultCodeEnum) {
        //  PERMISSION(209, "没有权限") ， 在页面显示内容
        //  第一个参数主题，第二个参数ResultCodeEnum
        Result<Object> result = Result.build(null, resultCodeEnum);
        //  将result 中的数据返回给页面
        //  "Content-Type", "application/json;charset=UTF-8"
        response.getHeaders().add("Content-Type","application/json;charset=UTF-8");
        DataBufferFactory dataBufferFactory = response.bufferFactory();
        //  json
        String outStr = JSON.toJSONString(result);
        DataBuffer wrap = dataBufferFactory.wrap(outStr.getBytes());

        //  Publisher
        //  返回数据
        return response.writeWith(Mono.just(wrap));
    }

    //  获取用户Id
    private String getUserId(ServerHttpRequest request) {
        //  用户Id 在缓存中存储
        //  user:login:token
        //  先获取token ,token 可能存在header 中，也可能存在cookie 中。
        String token = "";
        List<String> list = request.getHeaders().get("token");
        //  token 在header 中存在
        if (!CollectionUtils.isEmpty(list)){
            token = list.get(0);
        }else{
            //  从cookie获取token
            List<HttpCookie> httpCookiesList = request.getCookies().get("token");
            if (!CollectionUtils.isEmpty(httpCookiesList)){
                token = httpCookiesList.get(0).getValue();
            }
        }

        //  防止空指针
        if (!StringUtils.isEmpty(token)){
            // 组成key
            String userKey = "user:login:"+token;

            //  从缓存中获取数据
            //  在登录的时候 ： JSONObject userJson = new JSONObject(); "{\"ip\":\"192.168.200.1\",\"userId\":\"2\"}"
            String strJson = (String) redisTemplate.opsForValue().get(userKey);
            //  数据类型转换
            //  JSONObject jsonObject = JSON.parseObject(strJson, JSONObject.class);
            JSONObject jsonObject = JSON.parseObject(strJson);

            //  获取用户id
            if (jsonObject!=null){
                //  获取ip 地址
                String ip = (String) jsonObject.get("ip");
                if (ip.equals(IpUtil.getGatwayIpAddress(request))){
                    String userId = (String) jsonObject.get("userId");
                    //  返回数据
                    return userId;
                }else {
                    return "-1";
                }
            }
        }
        return null;
    }
}