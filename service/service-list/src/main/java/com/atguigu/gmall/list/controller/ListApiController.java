package com.atguigu.gmall.list.controller;

import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchParam;
import com.atguigu.gmall.model.list.SearchResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/**
 * @author luheng
 * @create 2020-12-09 16:17
 * @param:
 */
@RestController
@RequestMapping("api/list")
public class ListApiController {
    //  使用api ElasticsearchRestTemplate 来执行创建index，type
    @Autowired
    private ElasticsearchRestTemplate restTemplate;

    @Autowired
    private SearchService searchService;

    //  访问这个控制器的时候，自动创建index,type
    @GetMapping("inner/createIndex")
    public Result createIndex(){
        //  执行方法
        restTemplate.createIndex(Goods.class);
        restTemplate.putMapping(Goods.class);

        return Result.ok();
    }

    /**
     * 上架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/upperGoods/{skuId}")
    public Result upperGoods(@PathVariable("skuId") Long skuId) {
        searchService.upperGoods(skuId);
        return Result.ok();
    }

    /**
     * 下架商品
     * @param skuId
     * @return
     */
    @GetMapping("inner/lowerGoods/{skuId}")
    public Result lowerGoods(@PathVariable("skuId") Long skuId) {
        searchService.lowerGoods(skuId);
        return Result.ok();
    }

    //  远程调用接口
    @GetMapping("inner/incrHotScore/{skuId}")
    public Result incrHotScore(@PathVariable("skuId") Long skuId) {
        // 调用服务层
        searchService.incrHotScore(skuId);
        return Result.ok();
    }

    //  测试检索
    //  @RequestBody 接收json 数据，并将其转换为java 对象
    @PostMapping
    public Result list(@RequestBody SearchParam searchParam) throws Exception {
        SearchResponseVo response = searchService.search(searchParam);
        return Result.ok(response);
    }
}