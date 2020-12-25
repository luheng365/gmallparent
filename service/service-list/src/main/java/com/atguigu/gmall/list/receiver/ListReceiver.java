package com.atguigu.gmall.list.receiver;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.list.service.SearchService;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author luheng
 * @create 2020-12-21 18:00
 * @param:
 */
@Component
public class ListReceiver {

    @Autowired
    private SearchService searchService;

    //  设置监听消息
    @SneakyThrows
    @RabbitListener(bindings =@QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_UPPER,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS),
            key = {MqConst.ROUTING_GOODS_UPPER}
    ))
    public void upperGoods(Long skuId, Message message, Channel channel){
        /*
        1.  获取发送过来的消息内容
        2.  根据业务执行方法
         */
        if (skuId!=null){
            //  执行商品上架的方法
            searchService.upperGoods(skuId);

            //  确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }

    //  商品的下架
    @SneakyThrows
    @RabbitListener(bindings =@QueueBinding(
            value = @Queue(value = MqConst.QUEUE_GOODS_LOWER,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_GOODS),
            key = {MqConst.ROUTING_GOODS_LOWER}
    ))
    public void lowerGoods(Long skuId, Message message, Channel channel){
        /*
        1.  获取发送过来的消息内容
        2.  根据业务执行方法
         */
        if (skuId!=null){
            //  执行商品上架的方法
            searchService.lowerGoods(skuId);

            //  确认消息
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }
}
