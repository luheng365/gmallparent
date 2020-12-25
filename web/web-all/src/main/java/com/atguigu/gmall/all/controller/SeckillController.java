package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.activity.client.ActivityFeignClient;
import com.atguigu.gmall.common.result.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-23 16:36
 * @param:
 */
@Controller
public class SeckillController {


    @Autowired
    private ActivityFeignClient activityFeignClient;

    //  http://activity.gmall.com/seckill.html
    @GetMapping("seckill.html")
    public String seckillItem(Model model){
        //  ${list}
        Result result = activityFeignClient.findAll();

        model.addAttribute("list",result.getData());
        //  num - stock_count
        //  model.addAttribute("num",num);
        //  返回哪个页面?0
        return "seckill/index";
    }

    //  http://activity.gmall.com/seckill/38.html
    @GetMapping("seckill/{skuId}.html")
    public String seckillGoodsItem(@PathVariable Long skuId, Model model){
        //  ${item}
        Result result = activityFeignClient.getSeckillGoods(skuId);

        model.addAttribute("item",result.getData());
        return "seckill/item";
    }

    // http://activity.gmall.com/seckill/queue.html?skuId=38&skuIdStr=c81e728d9d4c2f636f067f89cc14862c
    @GetMapping("seckill/queue.html")
    public String queueItem(HttpServletRequest request){
        //  获取到传递过来的参数
        request.setAttribute("skuId",request.getParameter("skuId"));
        request.setAttribute("skuIdStr",request.getParameter("skuIdStr"));
        return "seckill/queue";
    }

    @GetMapping("seckill/trade.html")
    public String seckillTrade(Model model){
        //  页面需要userAddressList， detailArrayList，totalNum，totalAmount 这个四个数据都存在map中。然后使用model 保存
        Result<Map<String,Object>> result = activityFeignClient.trade();
        //        //  保存数据
        //        model.addAllAttributes(result.getData());
        //        //  返回下单页面
        //        return "seckill/trade";
        //  表示返回的200
        if (result.isOk()){
            //  保存数据
            model.addAllAttributes(result.getData());
            //  返回下单页面
            return "seckill/trade";
        }else {
            //  保存数据
            model.addAttribute("message","下单失败.");
            //  返回下单页面
            return "seckill/fail";
        }

    }

}
