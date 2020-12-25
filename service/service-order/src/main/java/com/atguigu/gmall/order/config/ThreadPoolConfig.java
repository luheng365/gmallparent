package com.atguigu.gmall.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author mqx
 * @date 2020-12-8 14:23:43
 */
@Configuration
public class ThreadPoolConfig {

    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        //  创建线程池
        return new ThreadPoolExecutor(
                3,
                5,
                3l,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(100)
        );
    }
}
