package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-17 14:50
 * @param:
 */
public interface CartService {
    //  添加购物车
    void addToCart(Long skuId, String userId, Integer skuNum);

    /**
     * 通过用户Id 查询购物车列表
     * @param userId
     * @param userTempId
     * @return
     */
    List<CartInfo> getCartList(String userId, String userTempId);

    /**
     * 选中状态变更
     * @param userId
     * @param isChecked
     * @param skuId
     */
    void checkCart(String userId, Integer isChecked, Long skuId);

    /**
     * 删除
     * @param userId
     * @param skuId
     */
    void deleteCartInfo(String userId, Long skuId);

    /**
     * 根据用户Id 查询购物车列表
     *
     * @param userId
     * @return
     */
    List<CartInfo> getCartCheckedList(String userId);
    //根据用户Id查询实时价格
    List<CartInfo> loadCartCache(String userId);

}
