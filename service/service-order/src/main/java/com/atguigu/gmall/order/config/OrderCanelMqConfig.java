package com.atguigu.gmall.order.config;

import com.atguigu.gmall.common.constant.MqConst;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;

/**
 * @author luheng
 * @create 2020-12-21 19:31
 * @param:
 */
@Configuration
public class OrderCanelMqConfig {

    //  声明一个交换机
    @Bean
    public CustomExchange delayExchange(){
        //  定义一个map
        HashMap<String, Object> map = new HashMap<>();
        //  key 不能随意改。
        map.put("x-delayed-type","direct");
        return new CustomExchange(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,"x-delayed-message",true,false,map);
    }
    //  声明一个队列

    @Bean
    public Queue delayQeue1(){
        return new Queue(MqConst.QUEUE_ORDER_CANCEL,true,false,false,null);
    }

    //  设置绑定关系
    @Bean
    public Binding delayBbinding1(){
        //   BindingBuilder.bind(queue1()).to(exchange()).with(routing_dead_1);
        //  BindingBuilder.GenericArgumentsConfigurer with = BindingBuilder.bind(delayQeue1()).to(delayExchange()).with(routing_delay);
        //  返回
        return BindingBuilder.bind(delayQeue1()).to(delayExchange()).with(MqConst.ROUTING_ORDER_CANCEL).noargs();
    }

}
