package com.atguigu.gmall.activity.service;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.activity.SeckillGoods;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-23 16:29
 * @param:
 */
public interface SeckillGoodsService {

    //  查询所有数据
    List<SeckillGoods> findAll();

    //  根据skuId 查询秒杀详情
    SeckillGoods findSeckillGoodsById(Long skuId);

    /**
     * 预下单interface
     * @param skuId
     * @param userId
     */
    void seckillOrder(Long skuId, String userId);

    //检查抢购状态
    Result checkOrder(Long skuId, String userId);

    //封装秒杀订单数据
    Result seckillTrade(String userId);
}
