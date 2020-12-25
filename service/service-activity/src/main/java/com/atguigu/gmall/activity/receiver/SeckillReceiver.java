package com.atguigu.gmall.activity.receiver;

import com.atguigu.gmall.activity.mapper.SeckillGoodsMapper;
import com.atguigu.gmall.activity.service.SeckillGoodsService;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.activity.SeckillGoods;
import com.atguigu.gmall.model.activity.UserRecode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * @author luheng
 * @create 2020-12-23 16:19
 * @param:
 */
@Component
public class SeckillReceiver {

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private SeckillGoodsService seckillGoodsService;

    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_1,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_1}
    ))
    public void importRedis(Message message, Channel channel){
        /*
        查询当前的数据放入缓存：
        审核状态必须通过status=1,start_time=new Date(),stock_count>0
         */
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        seckillGoodsQueryWrapper.eq("status",1);
        seckillGoodsQueryWrapper.eq("DATE_FORMAT(start_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        seckillGoodsQueryWrapper.gt("stock_count",0);
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        //  获取到当天的秒杀商品，放入缓存
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            //  必须了解数据类型 hash ，hset(key,field,value) key=seckill:goods,field=skuId,value=seckillGoods
            //  seckill:goods 38，39
            String seckillGoodsKey = RedisConst.SECKILL_GOODS;
            //  判断当前商品是否已经在缓存中。
            Boolean flag = redisTemplate.boundHashOps(seckillGoodsKey).hasKey(seckillGoods.getSkuId().toString());
            //  continue,return,break;
            if (flag){
                //  缓存中存在，不放入商品。
                continue;
            }
            //  没有则放入数据
            redisTemplate.boundHashOps(seckillGoodsKey).put(seckillGoods.getSkuId().toString(),seckillGoods);

            //  存储商品的数量：
            //  获取当前秒杀商品的库存数 redis - list
            for (Integer i = 0; i < seckillGoods.getStockCount(); i++) {
                //  RedisConst.SECKILL_STOCK_PREFIX = seckill:stock:skuId
                //  rpush/lpush key value  rpop key /lpop key  lrange key startindex,endindex.
                redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId()).leftPush(seckillGoods.getSkuId().toString());
                //  redisTemplate.boundListOps(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId()).rightPop();
            }

            //  发送消息给redis --- seckillpush 给每个商品进行了初始化{可以秒杀}
            redisTemplate.convertAndSend("seckillpush",seckillGoods.getSkuId()+":1");
            /*
                状态位：在map中。
                第一次操作：
                    启动了task，activity    会存储。

                第二次：
                    只启动activity     这个状态位还会存在么? 状态位会丢失。
             */
        }

        //  确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }

    //  监听消息：
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_SECKILL_USER,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_SECKILL_USER),
            key = {MqConst.ROUTING_SECKILL_USER}
    ))
    public void seckill(UserRecode userRecode, Message message, Channel channel){
        //  判断
        if(userRecode!=null){
            //  预下单：
            seckillGoodsService.seckillOrder(userRecode.getSkuId(),userRecode.getUserId());

            //  确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_TASK_18,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_TASK),
            key = {MqConst.ROUTING_TASK_18}
    ))
    public void delSeckillData(Message message, Channel channel){
        /*
          开始数据：审核状态必须通过status=1,start_time=new Date(),stock_count>0
          删除数据：秒杀结束的数据：审核状态必须通过status=1,end_time=new Date()
         */
        QueryWrapper<SeckillGoods> seckillGoodsQueryWrapper = new QueryWrapper<>();
        seckillGoodsQueryWrapper.eq("status",1);
        seckillGoodsQueryWrapper.eq("DATE_FORMAT(end_time,'%Y-%m-%d')", DateUtil.formatDate(new Date()));
        List<SeckillGoods> seckillGoodsList = seckillGoodsMapper.selectList(seckillGoodsQueryWrapper);
        //  如果你需要统计商品卖出的实际数量。那么就查询到list.size(); 同步到数据库;
        //  结束时秒杀的商品集合
        //  seckill:stock:38
        for (SeckillGoods seckillGoods : seckillGoodsList) {
            //  删除秒杀商品对应的库存数量
            redisTemplate.delete(RedisConst.SECKILL_STOCK_PREFIX+seckillGoods.getSkuId().toString());
        }

        //  还需要删除
        redisTemplate.delete(RedisConst.SECKILL_GOODS);
        redisTemplate.delete(RedisConst.SECKILL_ORDERS_USERS);
        //  预下单数据是否要删除。
        redisTemplate.delete(RedisConst.SECKILL_ORDERS);

        //  更新数据库中的状态 status=0

        SeckillGoods seckillGoods = new SeckillGoods();
        seckillGoods.setStatus("0");
        seckillGoodsMapper.update(seckillGoods,seckillGoodsQueryWrapper);

        //  确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }



}
