package com.atguigu.gmall.list.service;

/**
 * @author luheng
 * @create 2020-12-09 16:39
 * @param:
 */
public interface SearchService {

    /**
     * 上架商品列表
     * @param skuId
     */
    void upperGoods(Long skuId);

    /**
     * 下架商品列表
     * @param skuId
     */
    void lowerGoods(Long skuId);

    /**
     * 更新热点
     * @param skuId
     */
    void incrHotScore(Long skuId);


}
