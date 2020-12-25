package com.atguigu.gmall.cart.client;

import com.atguigu.gmall.cart.client.impl.CartDegradeFeignClient;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.cart.CartInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-17 21:44
 * @param:
 */
@FeignClient(value = "service-cart",fallback = CartDegradeFeignClient.class)
public interface CartFeignClient {

    //  将CartApiController中的数据发布到feign 上。
    @GetMapping("/api/cart/addToCart/{skuId}/{skuNum}")
    Result addToCart(@PathVariable("skuId") Long skuId, @PathVariable("skuNum") Integer skuNum);

    //      缺少一个request
    //    @GetMapping("/api/cart/addToCart/{skuId}/{skuNum}")
    //    Result addToCart(@PathVariable Long skuId,@PathVariable Integer skuNum,HttpServletRequest request);

    /**
     * 根据用户Id 查询购物车列表
     * @param userId
     * @return
     */
    @GetMapping("/api/cart/getCartCheckedList/{userId}")
    List<CartInfo> getCartCheckedList(@PathVariable("userId") String userId);

    //根据用户Id查询实时价格  【远程 调用】
    @GetMapping("/api/cart/loadCartCache/{userId}")
    Result loadCartCache(@PathVariable("userId") String userId);
}
