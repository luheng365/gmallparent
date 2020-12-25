package com.atguigu.gmall.all.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author luheng
 * @create 2020-12-17 10:00
 * @param:
 */
@Controller
public class PassportController {


    @GetMapping("login.html")
    public String login(HttpServletRequest request){

        String originUrl = request.getParameter("originUrl");


        request.setAttribute("originUrl",originUrl);
        //返回登录页面
        return "login";
    }
}
