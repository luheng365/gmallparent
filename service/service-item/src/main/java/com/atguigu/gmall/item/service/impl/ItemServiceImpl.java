package com.atguigu.gmall.item.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.item.service.ItemService;
import com.atguigu.gmall.list.client.ListFeignClient;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.product.SpuSaleAttr;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author luheng
 * @create 2020-12-04 16:12
 * @param:
 */
@Service
public class ItemServiceImpl implements ItemService {

    //  数据汇总
    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private ListFeignClient listFeignClient;

    //  http://item.gmall.com/39.html;  web-all 39.html  skuId = 39
    @Override
    public Map<String, Object> getBySkuId(Long skuId) {
        Map<String, Object> map = new HashMap<>();
        //  获取数据
        //  select * from sku_info where id =skuId;
        //创建一个线程对象 supplyAsync()方法没有参数有返回值
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            map.put("skuInfo",skuInfo);
            return skuInfo;
        },threadPoolExecutor);
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo) -> {
            //  获取分类数据
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            map.put("categoryView", categoryView);
        },threadPoolExecutor);

        CompletableFuture<Void> skuPriceCompletableFuture = CompletableFuture.runAsync(() -> {
            //  价格
            BigDecimal skuPrice = productFeignClient.getSkuPrice(skuId);
            map.put("price",skuPrice);
        },threadPoolExecutor);

        CompletableFuture<Void> spuSaleAttrListCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
            //  获取销售属性销售属性值回显并锁定
            List<SpuSaleAttr> spuSaleAttrListCheckBySku = productFeignClient.getSpuSaleAttrListCheckBySku(skuId, skuInfo.getSpuId());
            map.put("spuSaleAttrList", spuSaleAttrListCheckBySku);
        }),threadPoolExecutor);

        CompletableFuture<Void> MapCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {

            //  获取切换数据
            Map skuValueIdsMap = productFeignClient.getSkuValueIdsMap(skuInfo.getSpuId());
            //  skuValueIdsMap 转换为Json 字符串
            String mapJson = JSON.toJSONString(skuValueIdsMap);

            map.put("valuesSkuJson",mapJson);
        }),threadPoolExecutor);
        // web-all 需要渲染数据，页面需要获取对应的key。key 是谁 从页面找！  Model model model.addAllAttributes()
        //  分类数据的值 =  productFeignClient.get分类数据的方法
        //  map.put("分类数据的key","分类数据的值");

        CompletableFuture<Void> incrHotScoreCompletableFuture = CompletableFuture.runAsync(() -> {
            listFeignClient.incrHotScore(skuId);
        });

        //多任务组合
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewCompletableFuture,
                skuPriceCompletableFuture,
                spuSaleAttrListCompletableFuture,
                MapCompletableFuture,
                incrHotScoreCompletableFuture
        ).join();

        return map;
    }
}
