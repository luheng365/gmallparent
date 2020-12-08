package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.product.service.BaseTrademarkService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author luheng
 * @create 2020-12-01 13:42
 * @param:
 */
@Api(tags="spu后台分页接口")
@RestController
@RequestMapping("admin/product/baseTrademark")
public class SpuManageController {

    @Autowired
    private BaseTrademarkService baseTrademarkService;
    // 根据查询条件封装控制器
    // springMVC 的时候，有个叫对象属性传值 如果页面提交过来的参数与实体类的参数一致，
    // 则可以使用实体类来接收数据
    // http://api.gmall.com/admin/product/1/10?category3Id=61
    // @RequestBody 作用 将前台传递过来的json{"category3Id":"61"}  字符串变为java 对象。
    @GetMapping("{page}/{limit}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long limit
                                 ){

        //创建分页对象
        Page<BaseTrademark> spuInfoPage = new Page<>(page,limit);
        //获取数据
        IPage<BaseTrademark> baseTrademarkIPage = baseTrademarkService.selectPage(spuInfoPage);
        //将数据返回
        return Result.ok(baseTrademarkIPage);
    }
    //TradeMark新增 【保存添加操作】
    @PostMapping("save")
    public Result save(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.save(baseTrademark);
        return Result.ok();
    }
    //TradeMark品牌修改
    @PutMapping("update")
    public Result update(@RequestBody BaseTrademark baseTrademark){
        baseTrademarkService.updateById(baseTrademark);
        return Result.ok();
    }
    //TradeMark品牌删除
    @DeleteMapping("remove/{id}")
    public Result remove(@PathVariable Long id){
        baseTrademarkService.removeById(id);
        return Result.ok();
    }
    //TradeMark品牌查询 单个
    @GetMapping("get/{id}")
    public Result getMark(@PathVariable Long id){
        return Result.ok(baseTrademarkService.getById(id));
    }

    /**
     * 加载所有的品牌
     * @return
     */
    @GetMapping("getTrademarkList")
    public Result getTrademarkList() {
        List<BaseTrademark> trademarkList = baseTrademarkService.list(null);
        return Result.ok(trademarkList);
    }
}
