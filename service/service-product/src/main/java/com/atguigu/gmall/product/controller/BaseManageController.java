package com.atguigu.gmall.product.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author luheng
 * @create 2020-11-30 18:03
 * @param:
 */
@Api(tags = "后台管理接口")
@RestController
@RequestMapping("admin/product")
public class BaseManageController {
    @Autowired
    private ManageService manageService;

    /**
     * 查询所有的一级分类信息
     * @return
     */
    @GetMapping("getCategory1")
    public Result getCategory1(){

        List<BaseCategory1> baseCategory1List = manageService.findAll();
        return Result.ok(baseCategory1List);
    }
    /**
     * 根据一级分类Id 查询二级分类数据
     * @param category1Id
     * @return
     */
    //  http://api.gmall.com/admin/product/getCategory2/{category1Id}
    @GetMapping("getCategory2/{category1Id}")
    public Result getCategory2(@PathVariable Long category1Id){
        List<BaseCategory2> category2List = manageService.getCategory2(category1Id);
        //  将数据返回
        return Result.ok(category2List);
    }
    /**
     * 根据二级分类Id 查询三级分类数据
     * @param category2Id
     * @return
     */
    @GetMapping("getCategory3/{category2Id}")
    public Result getCategory3(@PathVariable Long category2Id){
        List<BaseCategory3> category3List = manageService.getCategory3(category2Id);
        //  将数据返回
        return Result.ok(category3List);
    }
    /**
     * 根据分类Id 获取平台属性数据
     * @param category1Id
     * @param category2Id
     * @param category3Id
     * @return
     */
    //  http://api.gmall.com/admin/product/attrInfoList/{category1Id}/{category2Id}/{category3Id}
    @GetMapping("attrInfoList/{category1Id}/{category2Id}/{category3Id}")
    public Result attrInfoList(@PathVariable Long category1Id,
                               @PathVariable Long category2Id,
                               @PathVariable Long category3Id){
        List<BaseAttrInfo> attrInfoList = manageService.getAttrInfoList(category1Id, category2Id, category3Id);
        //  将数据返回
        return Result.ok(attrInfoList);
    }

    /**
     * http://api.gmall.com/admin/product/saveAttrInfo
     * 保存平台属性方法
     * @param baseAttrInfo
     */
    @PostMapping("saveAttrInfo")
    public Result saveAttrInfo(@RequestBody BaseAttrInfo baseAttrInfo){
        //  调用服务层方法
        manageService.saveAttrInfo(baseAttrInfo);
        //  返回
        return Result.ok();
    }
    //  http://api.gmall.com/admin/product/getAttrValueList/{attrId}
    //  查询 平台属性值的集合
    //  select * from base_attr_value where attr_id = attrId;
    //  List<BaseAttrValue> baseAttrValueList =  manageService.getAttrValueList(attrId);
    @GetMapping("getAttrValueList/{attrId}")
    public Result getAttrValueList(@PathVariable Long attrId){

        //  select * from base_attr_value where attr_id = attrId;
        //List<BaseAttrValue> baseAttrValueList =  manageService.getAttrValueList(attrId);
        //  可以先通过attrId ，获取平台属性baseAttrInfo！    如果属性存在，则获取属性中对应的属性值 getAttrValueList();
        BaseAttrInfo baseAttrInfo = manageService.getBaseAttrInfo(attrId);

        return Result.ok(baseAttrInfo.getAttrValueList());
    }
    /**
     * spu分页查询
     * @param spuInfo
     * @return
     */
    @GetMapping("{page}/{limit}")
    public Result getSpuInfoPage(@PathVariable Long page,
                                 @PathVariable Long limit,
                                 SpuInfo spuInfo){

        Page<SpuInfo> spuInfoPage = new Page<>(page,limit);
        IPage<SpuInfo> spuInfoPageList = manageService.getSpuInfoPage(spuInfoPage, spuInfo);
        return Result.ok(spuInfoPageList);

    }
    /**
     * 【查询】加载所有的销售属性
     */
    @GetMapping("baseSaleAttrList")
    public Result savebaseSaleAttrList(){
        // 销售属性http://api.gmall.com/admin/product/baseSaleAttrList
       List<BaseSaleAttr> baseSaleAttrs = manageService.getBaseSaleAtteList();
        return Result.ok(baseSaleAttrs);
    }
    /**
     * 保存spu
     * @param spuInfo
     * @return
     */
    @PostMapping("saveSpuInfo")
    public Result saveSpuInfo(@RequestBody SpuInfo spuInfo){

        manageService.saveSpuInfo(spuInfo);
        return Result.ok();
    }

}
