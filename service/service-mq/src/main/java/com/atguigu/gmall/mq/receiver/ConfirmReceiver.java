package com.atguigu.gmall.mq.receiver;

import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * @author luheng
 * @create 2020-12-21 10:09
 * @param:
 */
@Component
public class ConfirmReceiver {
    //  监听消息，必须先设置好绑定关系！
    //  监听器注解
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "queue.confirm",durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = "exchange.confirm"),
            key = {"routing.confirm"}
    ))
    public void getMsg(String msg , Message message, Channel channel){

        System.out.println("接收的消息：\t"+msg);
        byte[] body = message.getBody();
        String str = new String(body);
        System.out.println(str);

        //  手动确认
        //  第一个参数 标识，第二个参数是否批量处理消息
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }
}
