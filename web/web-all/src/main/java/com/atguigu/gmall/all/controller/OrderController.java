package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.order.client.OrderFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-18 18:51
 * @param:
 */
@Controller
public class OrderController {

    @Autowired
    private OrderFeignClient orderFeignClient;

    @RequestMapping("trade.html")
    public String trade(Model model){

        Result<Map<String, Object>> result = orderFeignClient.trade();

        model.addAllAttributes(result.getData());

        return "order/trade";
    }

}
