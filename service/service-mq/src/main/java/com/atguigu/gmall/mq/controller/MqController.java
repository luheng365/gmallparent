package com.atguigu.gmall.mq.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.atguigu.gmall.mq.config.DelayedMqConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author luheng
 * @create 2020-12-21 10:08
 * @param:
 */
@RestController
@RequestMapping("mq")
public class MqController {

    @Autowired
    private RabbitService rabbitService;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    //  发送消息的控制器
    @RequestMapping("sendConfirm")
    public Result sendConfirm(){
        rabbitService.sendMessage("exchange.confirm","routing.confirm","来人了吧");
        return Result.ok();
    }

    //  测试发送延迟消息
    @GetMapping("sendDeadLettle")
    public Result sendDeadLettle(){
        //  设置一个时间戳
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        rabbitService.sendMessage(DeadLetterMqConfig.exchange_dead,DeadLetterMqConfig.routing_dead_1,"延迟了....");

        //  打印一个发送的时间
        System.out.println("消息发送了：\t"+simpleDateFormat.format(new Date()));

        return Result.ok();
    }

    //  基于延迟插件做的延迟消息发送
    @GetMapping("sendDelay")
    public Result sendDelay(){
        //  什么一个时间戳
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        //  如何设置的延迟时间
        rabbitTemplate.convertAndSend(DelayedMqConfig.exchange_delay, DelayedMqConfig.routing_delay, "来人了....准备。。。", new MessagePostProcessor() {
            @Override
            public Message postProcessMessage(Message message) throws AmqpException {
                //  在这个位置给消息设置过期时间 10 秒钟
                message.getMessageProperties().setDelay(10000);
                //  打印一个发送的时间
                System.out.println("消息发送了：\t"+simpleDateFormat.format(new Date()));
                //  返回消息
                return message;
            }
        });
        return Result.ok();
    }

}

