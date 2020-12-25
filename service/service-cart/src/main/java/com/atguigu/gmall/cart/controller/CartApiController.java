package com.atguigu.gmall.cart.controller;

import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author luheng
 * @create 2020-12-17 15:57
 * @param:
 */
@Api(tags = "测试购物车数据接口")
@RestController
@RequestMapping("api/cart")
public class CartApiController {

    @Autowired
    private CartService cartService;

    //  控制器是谁? item/index.html
    //  window.location.href = 'http://cart.gmall.com/addCart.html?skuId=' + this.skuId + '&skuNum=' + this.skuNum
    //  http://cart.gmall.com/addCart.html 这个应该写在web-all web-all 远程调用当前的对象
    //  url api/cart/addToCart/{skuId}/{skuNum}
    @GetMapping("addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable Long skuId,
                            @PathVariable Integer skuNum,
                            HttpServletRequest request){

        //  获取用户Id：网关中已经将用户Id放入请求头
        String userId = AuthContextHolder.getUserId(request);
        //  添加购物车：需要注意，登录可以添加，未登录也可以添加
        if(StringUtils.isEmpty(userId)){
            // 获取一个临时用户Id
            userId =  AuthContextHolder.getUserTempId(request);
        }

        cartService.addToCart(skuId,userId,skuNum);
        //  返回数据
        return Result.ok();
    }

    //   href="http://cart.gmall.com/cart.html"  页面需要一个cart.html 在web-all .
    @GetMapping("cartList")
    public Result cartList(HttpServletRequest request){
        //  获取用户Id+临时的用户Id
        String userId = AuthContextHolder.getUserId(request);
        String userTempId = AuthContextHolder.getUserTempId(request);
        List<CartInfo> cartList = cartService.getCartList(userId, userTempId);
        //  需要将cartList 放入result
        return Result.ok(cartList);
    }

    //  这个url 从哪里的?
    @GetMapping("checkCart/{skuId}/{isChecked}")
    public Result checkCart(@PathVariable Long skuId,
                            @PathVariable Integer isChecked,
                            HttpServletRequest request){
        //  获取用户Id：网关中已经将用户Id放入请求头
        String userId = AuthContextHolder.getUserId(request);
        //  添加购物车：需要注意，登录可以添加，未登录也可以添加
        if(StringUtils.isEmpty(userId)){
            // 获取一个临时用户Id
            userId =  AuthContextHolder.getUserTempId(request);
        }
        cartService.checkCart(userId,isChecked,skuId);
        //  返回数据
        return Result.ok();
    }

    //  删除购物车
    @DeleteMapping("deleteCart/{skuId}")
    public Result deleteCart(@PathVariable Long skuId,
                             HttpServletRequest request){
        //  获取用户Id：网关中已经将用户Id放入请求头
        String userId = AuthContextHolder.getUserId(request);
        //  添加购物车：需要注意，登录可以添加，未登录也可以添加
        if(StringUtils.isEmpty(userId)){
            // 获取一个临时用户Id
            userId =  AuthContextHolder.getUserTempId(request);
        }
        //  删除购物车数据
        cartService.deleteCartInfo(userId,skuId);
        //  返回Result
        return Result.ok();

    }
    //  根据用户Id 获取购物车列表。
    @GetMapping("getCartCheckedList/{userId}")
    public List<CartInfo> getCartCheckedList(@PathVariable String userId){
        return cartService.getCartCheckedList(userId);
    }
    //根据用户Id查询实时价格
    @GetMapping("loadCartCache/{userId}")
    public Result loadCartCache(@PathVariable("userId") String userId) {
        cartService.loadCartCache(userId);
        return Result.ok();
    }

}