package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author luheng
 * @create 2020-12-21 20:40
 * @param:
 */
@Controller
public class PaymentController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    //  http://payment.gmall.com/pay.html?orderId=105
    @GetMapping("pay.html")
    public String getPay(HttpServletRequest request){
        //  获取到orderId.
        String orderId = request.getParameter("orderId");

        OrderInfo orderInfo = orderFeignClient.getOrderInfo(Long.parseLong(orderId));
        //  保存数据
        request.setAttribute("orderInfo",orderInfo);
        return "payment/pay";
    }
    //  用户支付成功的控制器
    @GetMapping("pay/success.html")
    public String paySuccess(){
        //  返回支付成功页面
        return "payment/success";
    }
}
