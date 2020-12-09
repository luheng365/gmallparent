package com.atguigu.gmall.list.service.impl;

import com.atguigu.gmall.list.service.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.Goods;
import com.atguigu.gmall.model.list.SearchAttr;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * @author luheng
 * @create 2020-12-09 16:39
 * @param:
 */
@Service
public class SearchServiceImpl implements SearchService {

    @Autowired
    private ProductFeignClient productFeignClient;
    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private RedisTemplate redisTemplate;

    /**
     * 首页上架商品列表
     * @param skuId
     */
    @Override
    public void upperGoods(Long skuId) {
        //创建一个goods对象
        Goods goods = new Goods();
        //  此处goods 还是null，赋值
        //  获取skuInfo;
        CompletableFuture<SkuInfo> skuInfoCompletableFuture = CompletableFuture.supplyAsync(() -> {
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            //向goods中赋值
            goods.setId(skuId);
            goods.setTitle(skuInfo.getSkuName());
            goods.setDefaultImg(skuInfo.getSkuDefaultImg());
            goods.setPrice(skuInfo.getPrice().doubleValue());
            goods.setCreateTime(new Date());
            return skuInfo;
        });
        CompletableFuture<Void> categoryViewCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
            //查询分类信息
            BaseCategoryView categoryView = productFeignClient.getCategoryView(skuInfo.getCategory3Id());
            goods.setCategory1Id(categoryView.getCategory1Id());
            goods.setCategory1Name(categoryView.getCategory1Name());
            goods.setCategory2Id(categoryView.getCategory2Id());
            goods.setCategory2Name(categoryView.getCategory2Name());
            goods.setCategory3Id(categoryView.getCategory3Id());
            goods.setCategory3Name(categoryView.getCategory3Name());
        }));
        CompletableFuture<Void> trademarkCompletableFuture = skuInfoCompletableFuture.thenAcceptAsync((skuInfo -> {
            //获取品牌数据
            BaseTrademark trademark = productFeignClient.getTrademark(skuId);
            goods.setTmId(trademark.getId());
            goods.setTmName(trademark.getTmName());
            goods.setTmLogoUrl(trademark.getLogoUrl());
        }));
        CompletableFuture<Void> attrListCompletableFuture = CompletableFuture.runAsync(() -> {
            List<BaseAttrInfo> attrList = productFeignClient.getAttrList(skuId);
            List<SearchAttr> searchAttrList = new ArrayList<>();
            for (BaseAttrInfo baseAttrInfo : attrList) {
                SearchAttr searchAttr = new SearchAttr();
                searchAttr.setAttrId(baseAttrInfo.getId());
                searchAttr.setAttrName(baseAttrInfo.getAttrName());
                searchAttr.setAttrValue(baseAttrInfo.getAttrValueList().get(0).getValueName());
                searchAttrList.add(searchAttr);
            }
            goods.setAttrs(searchAttrList);

        });
        CompletableFuture.allOf(
                skuInfoCompletableFuture,
                categoryViewCompletableFuture,
                trademarkCompletableFuture,
                attrListCompletableFuture).join();
        //上架
        this.goodsRepository.save(goods);
    }
    /**
     * 首页下架商品列表
     * @param skuId
     */
    @Override
    public void lowerGoods(Long skuId) {
        this.goodsRepository.deleteById(skuId);
    }
    /**
     * 更新热点
     * @param skuId
     */
    @Override
    public void incrHotScore(Long skuId) {
        //定义key  确定使用的数据类型Zset
        String key = "hotScore";
        //获取热点信息
        Double hotScore = redisTemplate.opsForZSet().incrementScore(key, "skuId" + skuId, 1);
        //判断存储在缓存中的热度字段达到一定的值更新到es中
        if(hotScore%10==0){
            //更新es
            Optional<Goods> optional = goodsRepository.findById(skuId);
            Goods goods = optional.get();
            goods.setHotScore(hotScore.longValue());
            //保存
            this.goodsRepository.save(goods);
        }

    }
}
