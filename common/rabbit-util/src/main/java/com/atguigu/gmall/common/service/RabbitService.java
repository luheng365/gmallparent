package com.atguigu.gmall.common.service;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @author luheng
 * @create 2020-12-21 8:48
 * @param:
 */
@Service
public class RabbitService {

    //  发送消息必须有工具类
    @Autowired
    private RabbitTemplate rabbitTemplate;

    //  定义一个发送普通消息的方法
    public boolean sendMessage(String exchange, String routingKey, Object message){

        rabbitTemplate.convertAndSend(exchange,routingKey,message);
        //  默认返回
        return  true;
    }

    //  定义一个发送延迟消息的方法

    /**
     * 发送延迟消息
     * @param exchange
     * @param routingKey
     * @param message
     * @param delayTime
     * @return
     */
    public boolean sendDelayMessage(String exchange, String routingKey, Object message, int delayTime){
        //  如何设置的延迟时间
        rabbitTemplate.convertAndSend(exchange, routingKey, message, new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                //  在这个位置给消息设置过期时间 10 秒钟
                message.getMessageProperties().setDelay(delayTime*1000);
                //  返回消息
                return message;
            }
        });

        return true;
    }
}
