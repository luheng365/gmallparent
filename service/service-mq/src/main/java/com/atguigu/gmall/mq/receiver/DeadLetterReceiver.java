package com.atguigu.gmall.mq.receiver;

import com.atguigu.gmall.mq.config.DeadLetterMqConfig;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author luheng
 * @create 2020-12-21 18:28
 * @param:
 */
@Component
public class DeadLetterReceiver {

    //  监听消息
    @SneakyThrows
    @RabbitListener(queues = DeadLetterMqConfig.queue_dead_2)
    public void getMsg(String msg, Message message, Channel channel){
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        System.out.println(simpleDateFormat.format(new Date())+"接收到的消息：\t"+ msg);
        //  确认消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
    }
}
