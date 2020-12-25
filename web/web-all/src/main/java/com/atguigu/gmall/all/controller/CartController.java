package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;

/**
 * @author luheng
 * @create 2020-12-17 21:54
 * @param:
 */
@Controller
public class CartController {

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    //  编写控制器，添加的，还有一个查询购物车列表的。
    @RequestMapping("cart.html")
    public String cartList(){
        //  返回页面，需要存储数据么？  不需要了。因为异步调用！ "api/cart/cartList"
        return "cart/index";
    }

    //  浏览器：http://cart.atguigu.cn/addCart.html?skuId=5&skuNum=1  GET 请求
    //  将这个控制器中的业务逻辑做成重定向。
    @RequestMapping("addCart.html")
    public String addCart(HttpServletRequest request){
        //  根据skuId 查询skuInfo
        String skuId = request.getParameter("skuId");
        String skuNum = request.getParameter("skuNum");

        SkuInfo skuInfo = productFeignClient.getSkuInfo(Long.parseLong(skuId));
        cartFeignClient.addToCart(Long.parseLong(skuId),Integer.parseInt(skuNum));
        //  需要存储skuInfo
        request.setAttribute("skuInfo",skuInfo);
        //  存储skuNum
        request.setAttribute("skuNum",skuNum);
        //  返回页面
        //  return "redirect: addToCart"
        return "cart/addCart";
    }
}
