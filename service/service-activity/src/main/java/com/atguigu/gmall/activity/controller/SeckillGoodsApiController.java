package com.atguigu.gmall.activity.controller;

import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

/**
 * @author luheng
 * @create 2020-12-23 16:31
 * @param:
 */
@RestController
@RequestMapping("/api/activity/seckill")
public class SeckillGoodsApiController {

    //  注入服务层
    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 返回全部列表
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        return Result.ok(seckillGoodsService.findAll());
    }

    /**
     * 获取实体
     *
     * @param skuId
     * @return
     */
    @GetMapping("/getSeckillGoods/{skuId}")
    public Result getSeckillGoods(@PathVariable("skuId") Long skuId) {
        return Result.ok(seckillGoodsService.findSeckillGoodsById(skuId));
    }

    //  获取下单码地址：
    //  http://api.gmall.com/api/activity/seckill/auth/getSeckillSkuIdStr/38
    @GetMapping("auth/getSeckillSkuIdStr/{skuId}")
    public Result getSeckillSkuIdStr(@PathVariable Long skuId, HttpServletRequest request){
        //  skuIdStr 是啥?
        //  window.location.href = '/seckill/queue.html?skuId='+this.skuId+'&skuIdStr='+skuIdStr
        //  http://activity.gmall.com/seckill/queue.html?skuId=38&skuIdStr=c81e728d9d4c2f636f067f89cc14862c
        //  返回下单码，放入result中。
        //  下单码组成：对userId ，进行md5 加密。
        String userId = AuthContextHolder.getUserId(request);
        //  什么时候获取下单码 在活动开始之后，结束之前获取。
        //  获取到秒杀商品对象
        SeckillGoods seckillGoods = seckillGoodsService.findSeckillGoodsById(skuId);
        if (seckillGoods!=null){
            //  生成下单码
            Date curTime = new Date();  //  当前系统时间
            //  在活动开始之后，结束之前获取。
            if (DateUtil.dateCompare(seckillGoods.getStartTime(),curTime) &&
                    DateUtil.dateCompare(curTime,seckillGoods.getEndTime())){
                //  进行md5 加密。
                String skuIdStr = MD5.encrypt(userId);

                return Result.ok(skuIdStr);
            }
        }
        //  错误信息提示
        return Result.fail().message("获取下单码失败!");
    }

    //  保存订单 并发保存到数据库，只是将UserRecode数据发送到mq 中了。
    //  /api/activity/seckill/auth/seckillOrder/skuId?skuIdStr=skuIdStr
    @PostMapping("auth/seckillOrder/{skuId}")
    public Result seckillOrder(@PathVariable Long skuId,HttpServletRequest request){
        //  先获取url传递的下单码
        String skuIdStr = request.getParameter("skuIdStr");
        //  校验下单码： 将用户userId 进行md5 加密。
        String userId = AuthContextHolder.getUserId(request);
        if (!skuIdStr.equals(MD5.encrypt(userId))){
            //  非法请求
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }

        //  校验状态位：存在在内存  map.put(skuId,1)
        String stat = (String) CacheHelper.get(skuId.toString());
        //  null, 1, 0
        if (StringUtils.isEmpty(stat)){
            return Result.build(null, ResultCodeEnum.SECKILL_ILLEGAL);
        }
        if ("1".equals(stat)){
            //  还可以秒,将数据发送到mq中。
            UserRecode userRecode = new UserRecode();
            userRecode.setUserId(userId);
            userRecode.setSkuId(skuId);
            //  发送消息
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_SECKILL_USER,MqConst.ROUTING_SECKILL_USER,userRecode);
            //  返回200
            return Result.ok(userRecode);
        }else {
            return Result.build(null, ResultCodeEnum.SECKILL_FINISH);
        }
    }


    //  检查抢购状态
    @GetMapping("auth/checkOrder/{skuId}")
    public Result checkOrder(@PathVariable Long skuId,HttpServletRequest request){
        //  获取到用户Id
        String userId = AuthContextHolder.getUserId(request);
        //  返回数据
        return seckillGoodsService.checkOrder(skuId,userId);
    }

    //  下单页面展示控制器
    @GetMapping("auth/trade")
    public Result trade(HttpServletRequest request){
        //  获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        //  封装好的数据返回
        return seckillGoodsService.seckillTrade(userId);
    }

    //  提交订单
    //  获取到传递过来的数据
    @PostMapping("/auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request){
        //  获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));
        //  保存数据的时候，将数据放入数据库。
        Long orderId = orderFeignClient.submitOrder(orderInfo);
        if (orderId==null){
            return Result.fail().message("下单失败.");
        }
        //   将数据保存到缓存
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).put(userId,orderId.toString());
        //  删除用户购物车的key 数据,下单完成之后，可以继续秒杀第二个订单。灵活配置：
        redisTemplate.delete(RedisConst.SECKILL_USER+userId);
        //  删除预下单信息
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).delete(userId);
        //  返回orderId
        return Result.ok(orderId);
    }
}
