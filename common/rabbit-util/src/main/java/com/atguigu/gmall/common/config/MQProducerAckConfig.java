package com.atguigu.gmall.common.config;

/**
 * @author luheng
 * @create 2020-12-21 8:43
 * @param:
 */

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;


/**
 * @Description 消息发送确认
 * <p>
 * ConfirmCallback  只确认消息是否正确到达 Exchange 中
 * ReturnCallback   消息没有正确到达队列时触发回调，如果正确到达队列不执行
 * <p>
 * 1. 如果消息没有到exchange,则confirm回调,ack=false
 * 2. 如果消息到达exchange,则confirm回调,ack=true
 * 3. exchange到queue成功,则不回调return
 * 4. exchange到queue失败,则回调return
 *
 */
@Component
public class MQProducerAckConfig implements RabbitTemplate.ConfirmCallback ,RabbitTemplate.ReturnCallback {

    //  设置rabbitTemplate
    @Autowired
    private RabbitTemplate rabbitTemplate;

    //  表示在服务器启动的时候加载 ，在调用构造函数之后，初始化之前执行
    @PostConstruct
    public void init(){
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnCallback(this);
    }

    /**
     * 表示消息是否发送到了交换机
     * @param correlationData   消息的数据扩展类。
     * @param ack   表示消息是否发送到了交换机
     * @param cause 原因
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        //  判断消息是否发送到交换机
        if (ack){
            System.out.println("发送成功！");
        }else {
            System.out.println("发送失败！");
        }
    }

    /**
     * 这个方法表示如果消息没有正确发送到队列，才会执行！
     * @param message   消息的主题
     * @param replyCode 应答码
     * @param replyText 描述
     * @param exchange  交换机
     * @param routingKey    路由键
     */
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        //  正常来讲消息没有发送成功的时候，才会走这个方法。

        //  认为消息没有发送成功，也就是我可以在这个方法中自定义重发消息的方法！
        //  DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay
        //        if (exchange.equals("DelayedMqConfig.exchange_delay") && routingKey.equals("DelayedMqConfig.routing_delay")){
        //
        //            return;
        //        }

        //  表示重新发送消息。
        //  repalySendMsg(exchange,routingKey,message);

        System.out.println("消息主体: " + new String(message.getBody()));
        System.out.println("应答码: " + replyCode);
        System.out.println("描述：" + replyText);
        System.out.println("消息使用的交换器 exchange : " + exchange);
        System.out.println("消息使用的路由键 routing : " + routingKey);
    }
}