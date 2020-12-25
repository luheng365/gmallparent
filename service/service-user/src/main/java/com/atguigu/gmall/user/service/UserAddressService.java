package com.atguigu.gmall.user.service;

import com.atguigu.gmall.model.user.UserAddress;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-18 16:34
 * @param:
 */
public interface UserAddressService {

    /**
     * 根据用户Id 查询用户的收货地址列表！
     * @param userId
     * @return
     */
    List<UserAddress> findUserAddressListByUserId(String userId);
}
