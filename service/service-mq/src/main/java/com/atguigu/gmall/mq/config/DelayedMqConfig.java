package com.atguigu.gmall.mq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * @author luheng
 * @create 2020-12-21 18:39
 * @param:
 */
//基于插件的配置类
@Configuration
public class DelayedMqConfig {

    //  声明变量
    public static final String exchange_delay = "exchange.delay";
    public static final String routing_delay = "routing.delay";
    public static final String queue_delay_1 = "queue.delay.1";

    //  声明一个交换机
    @Bean
    public CustomExchange delayExchange(){
        //  定义一个map
        HashMap<String, Object> map = new HashMap<>();
        //  key 不能随意改。
        map.put("x-delayed-type","direct");
        return new CustomExchange(exchange_delay,"x-delayed-message",true,false,map);
    }
    //  声明一个队列

    @Bean
    public Queue delayQeue1(){
        return new Queue(queue_delay_1,true,false,false,null);
    }

    //  设置绑定关系
    @Bean
    public Binding delayBbinding1(){
        //   BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);
        //  BindingBuilder.GenericArgumentsConfigurer with = BindingBuilder.bind(delayQeue1()).to(delayExchange()).with(routing_delay);
        //  返回
        return BindingBuilder.bind(delayQeue1()).to(delayExchange()).with(routing_delay).noargs();
    }
}

