package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.model.cart.CartInfo;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * @author luheng
 * @create 2020-12-17 17:02
 * @param:
 */
@Service
public class CartAsyncServiceImpl implements CartAsyncService {

    // 引入mapper
    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Override
    @Async()
    public void updateCartInfo(CartInfo cartInfo) {
        //  update cart_info set sku_num=? where id = cartinfo.getId();
        //  update cart_info set sku_num = ? where sku_id = ? and user_id = ?
        System.out.println("update -- cartInfo");
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",cartInfo.getUserId());
        queryWrapper.eq("sku_id",cartInfo.getSkuId());
        //  cartInfo第一个参数相当于 ，第二个参数相当于更新条件
        cartInfoMapper.update(cartInfo,queryWrapper);
        //  不完美
        //  cartInfoMapper.updateById(cartInfo);
    }

    @Override
    @Async
    public void saveCartInfo(CartInfo cartInfo) {
        System.out.println("insert -- cartInfo");
        cartInfoMapper.insert(cartInfo);
    }

    @Override
    @Async
    public void deleteCartInfo(CartInfo cartInfo) {
        //  delete from cart_info where user_id = ?
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",cartInfo.getUserId());
        cartInfoMapper.delete(queryWrapper);
    }

    @Override
    @Async
    public void checkCart(String userId, Integer isChecked, Long skuId) {

        CartInfo cartInfo = new CartInfo();
        cartInfo.setIsChecked(isChecked);

        //  更新条件
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("sku_id",skuId);
        cartInfoMapper.update(cartInfo,queryWrapper);
    }

    //  删除谁的购物车哪个商品
    @Override
    @Async
    public void deleteCartInfo(String userId, Long skuId) {
        //  delete from cart_info where user_id = userId and sku_id = skuId;
        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id",userId);
        queryWrapper.eq("sku_id",skuId);

        cartInfoMapper.delete(queryWrapper);
    }
}
