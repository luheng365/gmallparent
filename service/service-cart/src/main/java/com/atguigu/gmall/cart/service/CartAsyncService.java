package com.atguigu.gmall.cart.service;

import com.atguigu.gmall.model.cart.CartInfo;

/**
 * @author luheng
 * @create 2020-12-17 17:00
 * @param:
 */
public interface CartAsyncService {
    /**
     * 修改购物车
     * @param cartInfo
     */
    void updateCartInfo(CartInfo cartInfo);

    /**
     * 保存购物车
     * @param cartInfo
     */
    void saveCartInfo(CartInfo cartInfo);

    /**
     * 删除数据
     * @param cartInfo
     */
    void deleteCartInfo(CartInfo cartInfo);

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
}
