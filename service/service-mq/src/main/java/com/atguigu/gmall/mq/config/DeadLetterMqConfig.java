package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
/**
 * @author luheng
 * @create 2020-12-21 18:13
 * @param:
 */
//  配置延迟消息
//  这个配置类相当于 @RabbitListener bindings
@Configuration
public class DeadLetterMqConfig {

    //  定义变量
    public static final String exchange_dead = "exchange.dead";
    public static final String routing_dead_1 = "routing.dead.1";
    public static final String routing_dead_2 = "routing.dead.2";
    public static final String queue_dead_1 = "queue.dead.1";
    public static final String queue_dead_2 = "queue.dead.2";

    //  设置交换机
    @Bean
    public DirectExchange exchange(){
        //  返回交换机
        return new DirectExchange(exchange_dead,true,false);
    }

    //  设置队列
    @Bean
    public Queue queue1(){
        HashMap<String, Object> map = new HashMap<>();
        //  设置交换机
        map.put("x-dead-letter-exchange",exchange_dead);
        //  如果发送了死信交换，则通过这个路由键routing_dead_2绑定到第二个队列。
        map.put("x-dead-letter-routing-key",routing_dead_2);
        //  表示当前队列中的消息ttl 10秒
        map.put("x-message-ttl",10000);
        //  第三个参数表示是否排外：true--只在本次连接中有效。第五个参数：表示是否有其他属性的设置。
        return  new Queue(queue_dead_1,true,false,false,map);
    }

    //  设置绑定关系 绑定的是队列一
    @Bean
    public Binding binding(){
        //  返回
        return BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);
    }

    @Bean
    public Queue queue2(){
        //  返回队列二
        return new Queue(queue_dead_2,true,false,false,null);
    }
    //  设置绑定关系 绑定的是队列二
    @Bean
    public Binding binding2(){
        //  返回
        return BindingBuilder.bind(queue2()).to(exchange()).with(routing_dead_2);
    }







}
