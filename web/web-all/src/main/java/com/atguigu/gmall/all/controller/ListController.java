package com.atguigu.gmall.all.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.list.SearchAttr;
import com.atguigu.gmall.model.list.SearchParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-16 16:47
 * @param:
 */
@Controller
public class ListController {

    @Autowired
    private ListFeignClient listFeignClient;

    //http://list.gmall.com/list.html?category3Id=61
    //http://list.gmall.com/list.html?keyword=小米手机

    @RequestMapping("list.html")
    public String list(SearchParam searchParam , Model model){
        //获取数据
        Result<Map> result = listFeignClient.list(searchParam);

        //记录查询条件
        String urlParam = this.makeUrlParam(searchParam);
        //存储品牌数据
        String trademarkParam = this.makeTrademark(searchParam.getTrademark());
        //存储平台属性值
        List<Map<String,Object>> propsParamList =this.makeProps(searchParam.getProps());
        //获取排序规则
        HashMap<String,String> orderMap = this.dealOrder(searchParam.getOrder());


        model.addAttribute("orderMap",orderMap);
        model.addAttribute("propsParamList",propsParamList);
        model.addAttribute("trademarkParam",trademarkParam);
        model.addAttribute("urlParam",urlParam);
        //存储数据
        model.addAllAttributes(result.getData());

        model.addAttribute("searchParam",searchParam);

        return "list/index";
    }
    //获取排序规则
    private HashMap<String, String> dealOrder(String order) {
        HashMap<String, String> hashMap = new HashMap<>();
        if(!StringUtils.isEmpty(order)){
            String[] split = order.split(":");
            if(split!=null&&split.length==2){
                hashMap.put("type",split[0]);
                hashMap.put("sort",split[1]);
            }else {
                hashMap.put("type","1");
                hashMap.put("sort","desc");
            }
        }else {
            hashMap.put("type","1");
            hashMap.put("sort","desc");
        }
        return hashMap;
    }

    //平台属性的面包屑制作
    private List<Map<String, Object>> makeProps(String[] props) {
        List<Map<String, Object>> list = new ArrayList<>();
        if(props!=null&&props.length>0){
            //得到每一个数据
            for (String prop : props) {
                //获取平台属性名，平台属性值名，平台属性Id
                String[] split = prop.split(":");
                if(split!=null&&split.length==3){
                    Map<String,Object> map = new HashMap<>();
                    map.put("attrId",split[0]);
                    map.put("attrValue",split[1]);
                    map.put("attrName",split[2]);
                    list.add(map);
                }
            }
        }

        return list;
    }

    //获取品牌的面包屑
    private String makeTrademark(String trademark) {
        if(!StringUtils.isEmpty(trademark)){
            //字符串分割
            String[] split = trademark.split(":");
            if(split!=null&& split.length==2){
                //获取品牌名称
                return "品牌:"+split[1];
            }

        }
        return null;
    }

    //记录查询条件
    private String makeUrlParam(SearchParam searchParam) {

        StringBuilder sb = new StringBuilder();
        //判断用户第一次根据什么查询的
        if(!StringUtils.isEmpty(searchParam.getKeyword())){
            sb.append("keyword=").append(searchParam.getKeyword());
        }
        if(!StringUtils.isEmpty(searchParam.getCategory3Id())){
            sb.append("category3Id=").append(searchParam.getCategory3Id());
        }
        if(!StringUtils.isEmpty(searchParam.getCategory2Id())){
            sb.append("category2Id=").append(searchParam.getCategory2Id());
        }
        if(!StringUtils.isEmpty(searchParam.getCategory1Id())){
            sb.append("category1Id=").append(searchParam.getCategory1Id());
        }
        //品牌
        if(!StringUtils.isEmpty(searchParam.getTrademark())){
            if(sb.length()>0){
                sb.append("&trademark=").append(searchParam.getTrademark());
            }
        }
        //品台属性
        String[] props = searchParam.getProps();
        if(props!=null&&props.length>0){
            //循环遍历
            for (String prop : props) {
                //prop相当一个单一的属性值查询
                if(sb.length()>0){
                    sb.append("&props=").append(prop);
                }
            }
        }


        return "list.html?"+sb.toString();
    }
}
