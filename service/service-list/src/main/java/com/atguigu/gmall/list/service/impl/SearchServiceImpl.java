package com.atguigu.gmall.list.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.list.service.GoodsRepository;
import com.atguigu.gmall.list.service.SearchService;
import com.atguigu.gmall.model.list.*;
import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.atguigu.gmall.model.product.BaseCategoryView;
import com.atguigu.gmall.model.product.BaseTrademark;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    //引入高级客户端
    @Autowired
    private RestHighLevelClient restHighLevelClient;

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

    @Override
    public SearchResponseVo search(SearchParam searchParam) throws Exception {
        /*
        1.  获取动态生成的dsl 语句
        2.  执行dsl 语句
        3.  获取结果
         */
        //  编写一个方法生成dsl语句，同时将dsl语句放入找个对象searchRequest
        SearchRequest searchRequest = this.buildQueryDsl(searchParam);
        //  执行dsl 语句
        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);

        //  数据结果转换
        SearchResponseVo searchResponseVo = this.parseSearchResult(searchResponse);
        //  先赋值一些明显的数据
        searchResponseVo.setPageNo(searchParam.getPageNo());
        searchResponseVo.setPageSize(searchParam.getPageSize());

        //  总页数：
        //  10,3,4  || 9,3,3
        //  long totalPages = searchResponseVo.getTotal()%searchParam.getPageSize()==0?searchResponseVo.getTotal()/searchParam.getPageSize():searchResponseVo.getTotal()/searchParam.getPageSize()+1
        //  (总记录数+每页显示的条数-1)/每页显示的条数
        long totalPages = (searchResponseVo.getTotal()+searchParam.getPageSize()-1)/searchParam.getPageSize();
        searchResponseVo.setTotalPages(totalPages);
        return searchResponseVo;
    }

    //  返回结果集
    private SearchResponseVo parseSearchResult(SearchResponse searchResponse) {
        /*
        private List<SearchResponseTmVo> trademarkList;
        private List<SearchResponseAttrVo> attrsList = new ArrayList<>();
        private List<Goods> goodsList = new ArrayList<>();
        private Long total;//总记录数
         */
        SearchResponseVo searchResponseVo = new SearchResponseVo();
        //  获取品牌数据 从聚合
        Map<String, Aggregation> tmIdMap = searchResponse.getAggregations().asMap();
        //  根据key 获取数据
        ParsedLongTerms tmIdAgg = (ParsedLongTerms) tmIdMap.get("tmIdAgg");
        //  获取品牌集合对象
        List<SearchResponseTmVo> trademarkList = tmIdAgg.getBuckets().stream().map((bucket) -> {
            //  声明一个品牌对象
            SearchResponseTmVo searchResponseTmVo = new SearchResponseTmVo();
            //  获取品牌的Id
            String tmId = ((Terms.Bucket) bucket).getKeyAsString();
            searchResponseTmVo.setTmId(Long.parseLong(tmId));
            // 获取品牌的名称
            ParsedStringTerms tmNameAgg = ((Terms.Bucket) bucket).getAggregations().get("tmNameAgg");
            String tmName = tmNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmName(tmName);

            // 获取品牌的url
            ParsedStringTerms tmLogoUrlAgg = ((Terms.Bucket) bucket).getAggregations().get("tmLogoUrlAgg");
            String tmLogoUrl = tmLogoUrlAgg.getBuckets().get(0).getKeyAsString();
            searchResponseTmVo.setTmLogoUrl(tmLogoUrl);

            return searchResponseTmVo;
        }).collect(Collectors.toList());

        //  赋值品牌数据
        searchResponseVo.setTrademarkList(trademarkList);

        //  赋值平台属性
        //  attrAgg 找个是nested
        ParsedNested attrAgg = (ParsedNested) tmIdMap.get("attrAgg");
        //  获取数据
        ParsedLongTerms attrIdAgg = attrAgg.getAggregations().get("attrIdAgg");
        //  使用stream
        List<SearchResponseAttrVo> attrsList = attrIdAgg.getBuckets().stream().map((bucket) -> {
            //  声明一个平台属性对象
            SearchResponseAttrVo searchResponseAttrVo = new SearchResponseAttrVo();
            Number attrId = ((Terms.Bucket) bucket).getKeyAsNumber();
            //  赋值平台属性Id
            searchResponseAttrVo.setAttrId(attrId.longValue());
            //  赋值平台属性名称
            ParsedStringTerms attrNameAgg = ((Terms.Bucket) bucket).getAggregations().get("attrNameAgg");
            String attrName = attrNameAgg.getBuckets().get(0).getKeyAsString();
            searchResponseAttrVo.setAttrName(attrName);
            //  赋值平台属性值名称
            ParsedStringTerms attrValueAgg = ((Terms.Bucket) bucket).getAggregations().get("attrValueAgg");
            List<? extends Terms.Bucket> buckets = attrValueAgg.getBuckets();

            //  声明一个集合
            //            List<String> strings = new ArrayList<>();
            //
            //            for (Terms.Bucket bucket1 : buckets) {
            //                String attrValue = bucket1.getKeyAsString();
            //                strings.add(attrValue);
            //            }
            //  获取平台属性值集合
            //  buckets.stream().map();  Terms.Bucket::getKeyAsString获取key .collect(Collectors.toList()) 将数据变成集合。
            searchResponseAttrVo.setAttrValueList(buckets.stream().map(Terms.Bucket::getKeyAsString).collect(Collectors.toList()));
            //  searchResponseAttrVo.setAttrValueList(strings);

            //  返回对象
            return searchResponseAttrVo;
        }).collect(Collectors.toList());

        searchResponseVo.setAttrsList(attrsList);

        //  获取hits
        SearchHits hits = searchResponse.getHits();
        SearchHit[] subHits = hits.getHits();
        //  声明一个集合来存储goods
        List<Goods> goodsList = new ArrayList<>();
        //  设置 goodsList
        if (subHits!=null && subHits.length>0){
            for (SearchHit subHit : subHits) {
                //  json 字符串
                String goodsSource = subHit.getSourceAsString();
                //  将找个json 字符串转换为对象Goods.class
                Goods goods = JSON.parseObject(goodsSource, Goods.class);
                //  但是，还缺少东东。
                //  判断是否有高亮
                if (subHit.getHighlightFields().get("title")!=null){
                    //  说明有高亮字段
                    Text title = subHit.getHighlightFields().get("title").getFragments()[0];
                    //  获取高亮字段
                    goods.setTitle(title.toString());
                }
                //  添加商品
                goodsList.add(goods);
            }
        }
        //  赋值商品数据
        searchResponseVo.setGoodsList(goodsList);
        //  赋值总记录数
        searchResponseVo.setTotal(hits.totalHits);
        //  返回数据
        return searchResponseVo;
    }

    //  返回查询对象
    private SearchRequest buildQueryDsl(SearchParam searchParam) {
        //  定义一个查询器{}
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        //  {query - bool}
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        //  判断是否根据分类Id 查询
        //  {filter -- term - category3Id }
        if (!StringUtils.isEmpty(searchParam.getCategory3Id())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category3Id",searchParam.getCategory3Id()));
        }
        if (!StringUtils.isEmpty(searchParam.getCategory2Id())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category2Id",searchParam.getCategory2Id()));
        }
        if (!StringUtils.isEmpty(searchParam.getCategory1Id())){
            boolQueryBuilder.filter(QueryBuilders.termQuery("category1Id",searchParam.getCategory1Id()));
        }

        //  判断是否根据关键词检索
        //  {bool - must - match}
        if (!StringUtils.isEmpty(searchParam.getKeyword())){
            boolQueryBuilder.must(QueryBuilders.matchQuery("title",searchParam.getKeyword()).operator(Operator.AND));
        }

        //  品牌
        if (!StringUtils.isEmpty(searchParam.getTrademark())){
            // trademark=2:华为
            //  StringUtils 不要使用spring 直接使用字符串
            String[] split = searchParam.getTrademark().split(":");
            if (split!=null && split.length==2){
                boolQueryBuilder.filter(QueryBuilders.termQuery("tmId",split[0]));
            }
        }
        //  平台属性值过滤
        //  props=23:4G:运行内存  平台属性Id 平台属性名，平台属性值名称
        String[] props = searchParam.getProps();
        if (props!=null && props.length>0){
            //  循环遍历
            for (String prop : props) {
                //  prop=23:4G:运行内存
                String[] split = prop.split(":");
                if (split!=null && split.length==3){
                    //  需要两个boolQuery 外层
                    BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                    // 嵌套查询子查询  内层
                    BoolQueryBuilder subBoolQuery = QueryBuilders.boolQuery();
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrId",split[0]));
                    subBoolQuery.must(QueryBuilders.termQuery("attrs.attrValue",split[1]));

                    boolQuery.must(QueryBuilders.nestedQuery("attrs",subBoolQuery, ScoreMode.None));
                    //  将nest的内嵌查询放入filter
                    boolQueryBuilder.filter(boolQuery);
                }
            }
        }
        //  {query}
        searchSourceBuilder.query(boolQueryBuilder);

        //  设置分页
        int from = (searchParam.getPageNo()-1)*searchParam.getPageSize();
        searchSourceBuilder.from(from);
        searchSourceBuilder.size(searchParam.getPageSize());

        //  高亮
        HighlightBuilder highlightBuilder = new HighlightBuilder();
        highlightBuilder.field("title");
        highlightBuilder.preTags("<span style=color:red>");
        highlightBuilder.postTags("</span>");
        searchSourceBuilder.highlighter(highlightBuilder);

        //  排序
        //  1:hotScore 2:price 页面传递参数的时候 ： 2:desc | 2:asc
        String order = searchParam.getOrder();
        if (!StringUtils.isEmpty(order)){
            //  获取到传递的数据并分割
            String[] split = order.split(":");
            //  判断 split[0]
            if (split!=null && split.length==2){
                //  声明一个字段 1 表示按照综合排序，2 表示价格排序
                String field = null;
                switch (split[0]){
                    case "1":
                        field="hotScore";
                        break;
                    case "2":
                        field="price";
                        break;
                }
                searchSourceBuilder.sort(field, "asc".equals(split[1])?SortOrder.ASC:SortOrder.DESC);
            }else {
                //  没有值
                searchSourceBuilder.sort("hotScore", SortOrder.DESC);
            }
        }
        //  什么都没有默认按照综合排序
        searchSourceBuilder.sort("hotScore", SortOrder.DESC);
        //  聚合
        //  聚合品牌：
        searchSourceBuilder.aggregation(AggregationBuilders.terms("tmIdAgg").field("tmId")
                .subAggregation(AggregationBuilders.terms("tmNameAgg").field("tmName"))
                .subAggregation(AggregationBuilders.terms("tmLogoUrlAgg").field("tmLogoUrl")));

        //  聚合平台属性： nested
        searchSourceBuilder.aggregation(AggregationBuilders.nested("attrAgg","attrs")
                .subAggregation(AggregationBuilders.terms("attrIdAgg").field("attrs.attrId")
                        .subAggregation(AggregationBuilders.terms("attrNameAgg").field("attrs.attrName"))
                        .subAggregation(AggregationBuilders.terms("attrValueAgg").field("attrs.attrValue"))));

        //  设置查询的数据选项{id,defaultImg,title,price}
        searchSourceBuilder.fetchSource(new String[] {"id","defaultImg","title","price"},null);

        //  GET /goods/info/_search
        SearchRequest searchRequest = new SearchRequest("goods");
        searchRequest.types("info");
        searchRequest.source(searchSourceBuilder);

        System.out.println("dsl:\t"+searchSourceBuilder.toString());

        return searchRequest;
    }
}
