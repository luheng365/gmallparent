package com.atguigu.gmall.activity.service.impl;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.activity.util.CacheHelper;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.result.ResultCodeEnum;
import com.atguigu.gmall.common.util.MD5;
import com.atguigu.gmall.model.activity.OrderRecode;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author luheng
 * @create 2020-12-23 16:30
 * @param:
 */
@Service
public class SeckillGoodsServiceImpl implements SeckillGoodsService {

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    @Override
    public List<SeckillGoods> findAll() {
        String seckillGoodsKey = RedisConst.SECKILL_GOODS;
        return redisTemplate.boundHashOps(seckillGoodsKey).values();
    }

    @Override
    public SeckillGoods findSeckillGoodsById(Long skuId) {
        //  hget(key,field);
        SeckillGoods seckillGoods = (SeckillGoods) redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).get(skuId.toString());
        return seckillGoods;
    }


    /**
     * 预下单interface
     * @param skuId
     * @param userId
     */
    @Override
    public void seckillOrder(Long skuId, String userId) {
        /*
        a.	判断状态位：
                String stat = map.get(skuId); 1 可以秒， 0 不可以秒.

        b.	判断当前用户是否是第一次秒杀该商品:
                setnx

        c.	预减库存 从缓存中的list中吐出一个。
                lpush, rpop

        d.	如果上述三步都没有问题，则认为你获取到了这个秒杀资格,将数据放入缓存。

        e.	更新一下真正的库存。
         */
        //  校验状态位：存在在内存  map.put(skuId,1)
        String stat = (String) CacheHelper.get(skuId.toString());
        //  表示不可以秒.
        if ("0".equals(stat)|| StringUtils.isEmpty(stat)){
            return;
        }

        //  redis -- setnx(key,value)   String 数据类型
        //  key=seckill:user:userId 表示哪个用户的秒杀 value = skuId
        //  秒杀列表 38，39
        //  String userSeckillKey = RedisConst.SECKILL_USER+userId+skuId;
        String userSeckillKey = RedisConst.SECKILL_USER+userId;

        //  判断当前用户是否是第一次秒杀该商品
        Boolean flag = redisTemplate.opsForValue().setIfAbsent(userSeckillKey, skuId, RedisConst.SECKILL__TIMEOUT, TimeUnit.SECONDS);
        //  不可以秒杀，已经存在当前用户
        if (!flag){
            return;
        }
        //  预减库存 从缓存中的list中吐出一个
        String goodsId = (String) redisTemplate.opsForList().rightPop(RedisConst.SECKILL_STOCK_PREFIX + skuId);
        //  判断是否预减成功
        if (StringUtils.isEmpty(goodsId)){
            //  已经没有库存了，通知其他兄弟节点
            redisTemplate.convertAndSend("seckillpush",skuId+":0");
            return;
        }
        //  如果上述三步都没有问题，则认为你获取到了这个秒杀资格, 将数据放入缓存。
        //  数据类型
        //  RedisConst.SECKILL_ORDERS   key=seckill:orders field=userId  value= OrderRecode
        //  这个对象记录了当前哪个用户，购买的哪个商品，以及商品的数据
        OrderRecode orderRecode = new OrderRecode();
        orderRecode.setUserId(userId);
        orderRecode.setNum(1);
        orderRecode.setSeckillGoods(this.findSeckillGoodsById(skuId));
        orderRecode.setOrderStr(MD5.encrypt(userId+skuId));
        //  表示有了秒杀的资格，表示秒杀成功。
        redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(userId,orderRecode);

        //  更新一下真正的库存，缓存,数据库有一个。
        this.updateStockCount(skuId);

    }
    //  检查抢购状态
    @Override
    public Result checkOrder(Long skuId, String userId) {
        /*
        1.  判断用户是否在缓存中存在   RedisConst.SECKILL_USER+userId
        2.  判断用户是否抢单成功      RedisConst.SECKILL_ORDERS 秒杀成功
        3.  判断用户是否下过订单      RedisConst.SECKILL_ORDERS_USERS 秒杀成了，并且已经下过订单
        4.  判断状态位               1,0
         */
        //  判断用户是否在缓存中存在
        String userSeckillKey = RedisConst.SECKILL_USER+userId;
        Boolean flag = redisTemplate.hasKey(userSeckillKey);
        //  表示缓存中有当前的用户信息
        if (flag){
            //  判断用户是否抢购成功
            //  hget(key,field)
            OrderRecode orderRecode  = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
            if (orderRecode!=null){
                //  表示秒杀成功
                return Result.build(orderRecode, ResultCodeEnum.SECKILL_SUCCESS);
            }
        }
        //  判断用户是否下过订单:下单时我们应该如何存储数据。
        //  hash  key = RedisConst.SECKILL_ORDERS_USERS seckill:orders:users 哪个用户秒杀的订单  field = userId, value = orderId
        Boolean result = redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).hasKey(userId);
        //  用户已经下过订单
        if (result){
            //  获取数据
            String  orderId = (String) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS_USERS).get(userId);
            //  返回数据
            return Result.build(orderId,ResultCodeEnum.SECKILL_ORDER_SUCCESS);
        }

        //  校验状态位：存在在内存  map.put(skuId,1)
        String stat = (String) CacheHelper.get(skuId.toString());
        if ("0".equals(stat)||StringUtils.isEmpty(stat)){
            //已售罄 抢单失败
            return Result.build(null, ResultCodeEnum.SECKILL_FAIL);
        }

        return Result.build(null,ResultCodeEnum.SECKILL_RUN);
    }
    //封装秒杀订单数据
    @Override
    public Result seckillTrade(String userId) {

        //  获取用户Id对应的收货地址列表。页面需要userAddressList
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);

        //  获取秒杀的商品明细detailArrayList -- OrderRecode
        //  创建一个订单明细的集合来存储秒杀商品
        List<OrderDetail> orderDetails = new ArrayList<>();
        //  redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).put(userId,orderRecode);
        OrderRecode orderRecode  = (OrderRecode) redisTemplate.boundHashOps(RedisConst.SECKILL_ORDERS).get(userId);
        SeckillGoods seckillGoods = null;
        if (orderRecode!=null){
            //  获取到用户秒杀的商品
            seckillGoods = orderRecode.getSeckillGoods();
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuNum(seckillGoods.getNum());
            //  原始价格
            //  orderDetail.setOrderPrice(seckillGoods.getPrice());
            orderDetail.setOrderPrice(seckillGoods.getCostPrice());
            orderDetail.setImgUrl(seckillGoods.getSkuDefaultImg());
            orderDetail.setSkuName(seckillGoods.getSkuName());
            orderDetail.setSkuId(seckillGoods.getSkuId());
            //  秒杀不需要验证库存了。
            //  orderDetail.setHasStock();
            orderDetails.add(orderDetail);
        }

        //  原来怎么写的：
        //        OrderInfo orderInfo = new OrderInfo();
        //        orderInfo.setOrderDetailList(orderDetails);
        //        orderInfo.sumTotalAmount();
        HashMap<String, Object> map = new HashMap<>();
        map.put("userAddressList",userAddressList);
        map.put("detailArrayList",orderDetails);
        map.put("totalNum",1);
        map.put("totalAmount",seckillGoods.getCostPrice());
        //  map.put("totalAmount",orderInfo.getTotalAmount());

        return Result.ok(map);
    }

    /**
     * 更新库存
     * @param skuId
     */
    private void updateStockCount(Long skuId) {
        //  seckill:stock:38
        Long stockCount = redisTemplate.opsForList().size(RedisConst.SECKILL_STOCK_PREFIX + skuId);
        //  减库存缓冲
        if (stockCount%2==0){
            //  更新缓存+数据库
            //  update seckill_goods set stock_count=stockCount where sku_id = skuId;
            //            SeckillGoods seckillGoods = new SeckillGoods();
            //            seckillGoods.setStockCount(stockCount.intValue());
            //            QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
            //            seckillGoodsQueryWrapper.eq("sku_id",skuId);
            //            seckillGoodsMapper.update(seckillGoods,seckillGoodsQueryWrapper);
            //  直接从缓存获取到数据。

            //  update seckill_goods set stock_count=stockCount where id = id;
            SeckillGoods seckillGoods = this.findSeckillGoodsById(skuId);
            seckillGoods.setStockCount(stockCount.intValue());
            seckillGoodsMapper.updateById(seckillGoods);

            //  根据skuId，查询seckillGoods.
            //  更新缓存
            //  hash key= seckill:goods field=skuId value=SeckillGoods
            //  hset(key,field,value);
            redisTemplate.boundHashOps(RedisConst.SECKILL_GOODS).put(skuId.toString(),seckillGoods);
        }

    }
}
