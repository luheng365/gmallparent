package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;


/**
 * @author luheng
 * @create 2020-12-08 20:40
 * @param:
 */
@Controller
public class IndexController {

    @Autowired
    private ProductFeignClient productFeignClient;
    //商城首页
    @RequestMapping({"/","index.html"})
    public String index(Model model){
        // 获取首页分类数据
        Result result = productFeignClient.getBaseCategoryList();
        model.addAttribute("list",result.getData());
        return "index/index";
    }
}
