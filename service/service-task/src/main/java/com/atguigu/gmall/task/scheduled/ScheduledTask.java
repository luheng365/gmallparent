package com.atguigu.gmall.task.scheduled;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author luheng
 * @create 2020-12-23 16:15
 * @param:
 */
@Component
@EnableScheduling
public class ScheduledTask {

    @Autowired
    private RabbitService rabbitService;
    //  分，时，日，月，周，年
    //  每个10触发一次
    @Scheduled(cron = "0/10 * * * * ?")
    public void sendMsg(){
        //  消费者，需要将数据库中的数据导入缓存。skuId,
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_1,"到点了，该做事了.");
    }

    //  18 点结束秒杀：然后清空缓存
    @Scheduled(cron = "0 0 1 * * ?")
    public void sendSeckillEndMsg(){
        //  消费者，需要将数据库中的数据导入缓存。skuId,
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_TASK,MqConst.ROUTING_TASK_18,"seckill over");
    }
}
