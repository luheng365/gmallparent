package com.atguigu.gmall.product.service.impl;

import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.model.product.*;
import com.atguigu.gmall.product.mapper.*;
import com.atguigu.gmall.product.service.ManageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.lettuce.core.RedisClient;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * @author luheng
 * @create 2020-11-30 17:50
 * @param:
 */
@Service
public class ManageServiceImpl implements ManageService {
    @Autowired
    private BaseCategory1Mapper baseCategory1Mapper;
    @Autowired
    private BaseCategory2Mapper baseCategory2Mapper;
    @Autowired
    private BaseCategory3Mapper baseCategory3Mapper;
    @Autowired
    private BaseAttrInfoMapper baseAttrInfoMapper;
    @Autowired
    private BaseAttrValueMapper baseAttrValueMapper;

    @Autowired
    private BaseSaleAttrMapper baseSaleAttrMapper;


    @Autowired
    private SpuInfoMapper spuInfoMapper;
    @Autowired
    private SpuImageMapper spuImageMapper;
    @Autowired
    private SpuSaleAttrMapper spuSaleAttrMapper;
    @Autowired
    private SpuSaleAttrValueMapper spuSaleAttrValueMapper;


    @Autowired
    private SkuInfoMapper skuInfoMapper;
    @Autowired
    private SkuAttrValueMapper skuAttrValueMapper;
    @Autowired
    private SkuSaleAttrValueMapper skuSaleAttrValueMapper;
    @Autowired
    private SkuImageMapper skuImageMapper;
//***************商品详情实现*************************************************
    @Autowired
    private BaseCategoryViewMapper baseCategoryViewMapper;


    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private RedissonClient redissonClient;


    //加载所有的一级分类
    @Override
    public List<BaseCategory1> findAll() {
        return baseCategory1Mapper.selectList(null);
    }
    //加载所有的二级分类
    @Override
    public List<BaseCategory2> getCategory2(Long category1Id) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("category1_id",category1Id);
        List<BaseCategory2> baseCategory2List = baseCategory2Mapper.selectList(wrapper);
        return baseCategory2List;
    }
    //加载所有的三级分类
    @Override
    public List<BaseCategory3> getCategory3(Long category2Id) {
        QueryWrapper wrapper = new QueryWrapper();
        wrapper.eq("category2_id",category2Id);
        List<BaseCategory3> baseCategory3List = baseCategory3Mapper.selectList(wrapper);
        return baseCategory3List;
    }
    //加载查询1级、2级、3级的属性
    @Override
    public List<BaseAttrInfo> getAttrInfoList(Long category1Id, Long category2Id, Long category3Id) {
        // 调用mapper：
        return baseAttrInfoMapper.selectBaseAttrInfoList(category1Id, category2Id, category3Id);
    }
    //保存 （添加）开启事务操作[既可以保存又可以修改]
    @Override
    @Transactional
    public void saveAttrInfo(BaseAttrInfo baseAttrInfo) {
        if(baseAttrInfo.getId()==null){
            //新增属性 保存
            baseAttrInfoMapper.insert(baseAttrInfo);
        }else{
            //修改
            baseAttrInfoMapper.updateById(baseAttrInfo);
        }
        //如下操作 操作baseAttrValue
        //先删除在插入数据  //删除完成之后，原始的id不见了
        QueryWrapper<BaseAttrValue> baseAttrValueQueryWrapper = new QueryWrapper<>();
        baseAttrValueQueryWrapper.eq("attr_id",baseAttrInfo.getId());
        baseAttrValueMapper.delete(baseAttrValueQueryWrapper);

        //新增属性值保存
        List<BaseAttrValue> attrValueList = baseAttrInfo.getAttrValueList();
        if (!CollectionUtils.isEmpty(attrValueList)){
            for (BaseAttrValue baseAttrValue : attrValueList) {
                //attrId 赋值baseAttrInfo.id = baseAttrValue.attrId
                baseAttrValue.setAttrId(baseAttrInfo.getId());
                baseAttrValueMapper.insert(baseAttrValue);
            }
        }
    }
    /**
     * 根据平台id获取平台属性值
     * 数据回显
     * @param attrId
     * @return
     */
    @Override
    public List<BaseAttrValue> getAttrValueList(Long attrId) {
        return baseAttrValueMapper.selectList(new QueryWrapper<BaseAttrValue>().eq("attr_id",attrId));
    }
    /**
     * 根据平台属性id 获取平台属性对象
     * @param attrId
     * @return
     */
    @Override
    public BaseAttrInfo getBaseAttrInfo(Long attrId) {
        // select * from base_attr_info where id = attrId
        BaseAttrInfo baseAttrInfo = baseAttrInfoMapper.selectById(attrId);
        if(baseAttrInfo!=null){
            //获取平台属性集合，将属性值集合放入对象
            baseAttrInfo.setAttrValueList(getAttrValueList(attrId));
        }
        return baseAttrInfo;
    }
    /**
     * spu分页查询
     * @param pageParam
     * @param spuInfo
     * @return
     */
    @Override
    public IPage<SpuInfo> getSpuInfoPage(Page<SpuInfo> pageParam, SpuInfo spuInfo) {
        QueryWrapper<SpuInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("category3_id", spuInfo.getCategory3Id());
        queryWrapper.orderByDesc("id");
        return spuInfoMapper.selectPage(pageParam, queryWrapper);
    }

    /**
     * 加载所有的销售属性
     * @return
     */
    @Override
    public List<BaseSaleAttr> getBaseSaleAtteList() {
        return baseSaleAttrMapper.selectList(null);
    }

    /**
     * 保存spu
     * @param spuInfo
     * @return
     */
    @Override
    @Transactional //多表保存 开启事务
    public void saveSpuInfo(SpuInfo spuInfo) {
        //获取商品表
        spuInfoMapper.insert(spuInfo);
        //获取商品图片表集合
        List<SpuImage> spuImageList = spuInfo.getSpuImageList();
        //判断集合是否为空
        if(!CollectionUtils.isEmpty(spuImageList)){
            //遍历集合  插入图片
            for (SpuImage spuImage : spuImageList) {
                spuImage.setSpuId(spuInfo.getId());
                spuImageMapper.insert(spuImage);
            }
        }
        //获取spu销售属性表
        List<SpuSaleAttr> spuSaleAttrList = spuInfo.getSpuSaleAttrList();
        //判断销售属性是否为空
        if(!CollectionUtils.isEmpty(spuSaleAttrList)){
            //遍历添加销售属性
            for (SpuSaleAttr spuSaleAttr : spuSaleAttrList) {
                //设置销售属性spu_id = 商品id
                spuSaleAttr.setSpuId(spuInfo.getId());
                spuSaleAttrMapper.insert(spuSaleAttr);

                //销售属性值表spuSaleAttrValue
                List<SpuSaleAttrValue> spuSaleAttrValueList = spuSaleAttr.getSpuSaleAttrValueList();
                if(!CollectionUtils.isEmpty(spuSaleAttrValueList)){
                    for (SpuSaleAttrValue spuSaleAttrValue : spuSaleAttrValueList) {
                        //销售属性值Spu_id = 商品spuInfo的id
                        spuSaleAttrValue.setSpuId(spuInfo.getId());
                        //销售属性值名称= 销售属性名称
                        spuSaleAttrValue.setSaleAttrName(spuSaleAttr.getSaleAttrName());
                        spuSaleAttrValueMapper.insert(spuSaleAttrValue);
                    }
                }

            }
        }

    }
    /**
     * 保存sku
     * skuInfo
     * skuAttrValue : sku 与平台属性值的关系！
     * skuSaleAttrValue : sku 与销售属性的关系！
     * skuImage : 库存图片表！
     * @param skuInfo
     * @return
     */
    @Transactional
    @Override
    public void saveSkuInfo(SkuInfo skuInfo) {
        skuInfoMapper.insert(skuInfo);
        List<SkuImage> skuImageList = skuInfo.getSkuImageList();
        if(!CollectionUtils.isEmpty(skuImageList)){
            for (SkuImage skuImage : skuImageList) {
                //设置图片的skuId=库存sku的id
                skuImage.setSkuId(skuInfo.getId());
                skuImageMapper.insert(skuImage);
            }
        }
        List<SkuAttrValue> skuAttrValueList = skuInfo.getSkuAttrValueList();
        if(!CollectionUtils.isEmpty(skuAttrValueList)){
            for (SkuAttrValue skuAttrValue : skuAttrValueList) {
                //平台属性的skuId=库存sku的id
                skuAttrValue.setSkuId(skuInfo.getId());
                skuAttrValueMapper.insert(skuAttrValue);
            }
        }
        List<SkuSaleAttrValue> skuSaleAttrValueList = skuInfo.getSkuSaleAttrValueList();
        if(!CollectionUtils.isEmpty(skuSaleAttrValueList)){
            for (SkuSaleAttrValue skuSaleAttrValue : skuSaleAttrValueList) {
                skuSaleAttrValue.setSkuId(skuInfo.getId());
                skuSaleAttrValue.setSpuId(skuInfo.getSpuId());
            }
        }

    }

    /**
     * 根据spuId 查询spuImageList
     * @param spuId
     * @return
     */
    @Override
    public List<SpuImage> getSpuImageList(Long spuId) {
        QueryWrapper<SpuImage> wrapper = new QueryWrapper<>();
        wrapper.eq("spu_id",spuId);
        return spuImageMapper.selectList(wrapper);
    }

    /**
     * SKU分页列表
     * @return
     */
    @Override
    public IPage<SkuInfo> getPageList(Page<SkuInfo> skuInfoPage) {
        QueryWrapper<SkuInfo> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("id");
        return skuInfoMapper.selectPage(skuInfoPage,wrapper);

    }
    /**
     * 商品上架
     * @param skuId
     * @return
     */
    @Override
    public void onSale(Long skuId) {
        //更改销售状态
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(1);
        skuInfoMapper.updateById(skuInfo);
    }
    /**
     * 商品下架
     * @param skuId
     * @return
     */
    @Override
    public void cancelSale(Long skuId) {
        //更改销售状态
        SkuInfo skuInfo = new SkuInfo();
        skuInfo.setId(skuId);
        skuInfo.setIsSale(0);
        skuInfoMapper.updateById(skuInfo);
    }

    /**
     * 根据spuId 获取销售属性+销售属性值
     * @param spuId
     * @return
     */
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrList(Long spuId) {
        //调用mapper
        return spuSaleAttrMapper.selectSpuSaleAttrList(spuId);
    }
//*********************商品详情实现***************************************************************************************
    /**
     * 根据skuId 查询skuInfo
     * 查询图片列表集合
     * @param skuId
     * @return
     */
    @Override
    public SkuInfo getSkuInfo(Long skuId) {

        return getSkuInfoRedisson(skuId);

        //return getSkuInfoRedis(skuId);
    }

    private SkuInfo getSkuInfoRedisson(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            //获取数据【set(key,value)  get(key)】
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            //判断
            if(skuInfo==null) {//缓存中没有数据,从数据库取值，需要加锁【分布式锁】 ****目的防止缓存击穿
                //缓存中没有数据，查询数据库 上分布式锁
                //声明一个锁的key
                String skuKeyLock = RedisConst.SKUKEY_PREFIX + skuId +RedisConst.SKULOCK_SUFFIX;
                //获取锁的对象
                RLock lock = redissonClient.getLock(skuKeyLock);
                boolean res = lock.tryLock(RedisConst.SKULOCK_EXPIRE_PX1, RedisConst.SKULOCK_EXPIRE_PX2, TimeUnit.SECONDS);//上锁
                //上锁成功
                if(res){
                    try {
                        //业务逻辑
                        //上锁成功，之行业务从数据库获取数据
                        skuInfo = getSkuInfoDB(skuId);
                        //加锁：防止缓存穿透
                        if(skuInfo==null){
                            SkuInfo skuInfo1 = new SkuInfo();
                            redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                            //返回空数据
                            return skuInfo1;
                        }
                        //将数据放入缓存
                        redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);
                        return skuInfo;//表示查询到数据
                    } finally {
                        lock.unlock();
                    }
                }else{
                    //等待自旋
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    return getSkuInfo(skuId);
                }
            }else {
                    return skuInfo;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //数据库兜底
        return getSkuInfoDB(skuId);
    }

    private SkuInfo getSkuInfoRedis(Long skuId) {
        SkuInfo skuInfo = null;
        try {
            //先获取缓存中的数据 定义缓存的key sku:skuId:info
            //缓存存储数据时，存储的数据时 SkuInfo  使用String类型更为简单[前端只做展示]
            String skuKey = RedisConst.SKUKEY_PREFIX + skuId + RedisConst.SKUKEY_SUFFIX;
            //获取数据【set(key,value)  get(key)】
            skuInfo = (SkuInfo) redisTemplate.opsForValue().get(skuKey);
            //判断
            if(skuInfo==null){//缓存中没有数据,从数据库取值，需要加锁【分布式锁】 ****目的防止缓存击穿
            //声明一个锁的key
            String skuKeyLock = RedisConst.SKUKEY_PREFIX + skuId +RedisConst.SKULOCK_SUFFIX;
            //声明一个UUID
            String uuid = UUID.randomUUID().toString();
            //开始上锁
            Boolean flag = redisTemplate.opsForValue().setIfAbsent(skuKeyLock, uuid, RedisConst.SKULOCK_EXPIRE_PX1, TimeUnit.SECONDS);
            //判断上锁成功
            if(flag){
                //上锁成功，之行业务从数据库获取数据
             skuInfo = getSkuInfoDB(skuId);
             //加锁：防止缓存穿透
             if(skuInfo==null){
                 SkuInfo skuInfo1 = new SkuInfo();
                 redisTemplate.opsForValue().set(skuKey,skuInfo1,RedisConst.SKUKEY_TEMPORARY_TIMEOUT,TimeUnit.SECONDS);
                 //返回空数据
                 return skuInfo1;
             }
             //将数据放入缓存
             redisTemplate.opsForValue().set(skuKey,skuInfo,RedisConst.SKUKEY_TIMEOUT,TimeUnit.SECONDS);

                String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
                // 设置lua脚本返回的数据类型
                DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
                // 设置lua脚本返回类型为Long
                redisScript.setResultType(Long.class);
                redisScript.setScriptText(script);
                // 删除key 所对应的 value
                redisTemplate.execute(redisScript, Arrays.asList(skuKeyLock),uuid);

             return skuInfo;
            }else{
                //等待自旋
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
               return getSkuInfo(skuId);
            }

            }else {
                //缓存中有数据
                return skuInfo;
            }
        } catch (Exception e) {
            //log 日志...... 通知运维维修
            e.printStackTrace();
        }
        //如果发生异常，数据库兜底
        return getSkuInfoDB(skuId);
    }

    //ctrl + alt + m 提取内部内容
    private SkuInfo getSkuInfoDB(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        if(skuInfo!=null){
            // 根据skuId 查询图片列表集合
            QueryWrapper<SkuImage> wrapper = new QueryWrapper<>();
            wrapper.eq("sku_id",skuId);
            List<SkuImage> skuImageList = skuImageMapper.selectList(wrapper);
            skuInfo.setSkuImageList(skuImageList);
        }
        return skuInfo;
    }

    /**
     * 通过三级分类id查询分类信息【所有的分类信息】
     * @param category3Id
     * @return
     */
    @Override
    public BaseCategoryView getCategoryViewByCategory3Id(Long category3Id) {
        return baseCategoryViewMapper.selectById(category3Id);
    }
    /**
     * 获取sku最新价格
     * @param skuId
     * @return
     */
    @Override
    public BigDecimal getSkuPrice(Long skuId) {
        SkuInfo skuInfo = skuInfoMapper.selectById(skuId);
        //判断skuInfo是否为空
        if(skuInfo!=null){
            return skuInfo.getPrice();
        }else{
            return new BigDecimal(0);
        }
    }
    /**
     * 根据spuId，skuId 查询销售属性+销售属性值+锁定销售属性
     * 返回值是销售属性
     * @param skuId
     * @param spuId
     * @return
     */
    @Override
    public List<SpuSaleAttr> getSpuSaleAttrListCheckBySku(Long skuId, Long spuId) {
        return spuSaleAttrMapper.selectSpuSaleAttrListCheckBySku(skuId,spuId);
    }
    /**
     * 根据spuId 查询map 集合属性 汇总供item调用
     * @param spuId
     * @return
     */
    @Override
    public Map getSkuValueIdsMap(Long spuId) {
        //{"99|100":"38","99|101":"39"} map.put("99|100","38");
        HashMap<Object, Object> hashMap = new HashMap<>();
        //  存储数据 {"99|100":"38","99|101":"39"} map.put("99|100","38");
        //  通过 mapper 查询数据，并将其放入map 集合  分析使用哪个mapper？
        //  第一种解决方案：自定义一个 Ssav DTO; skuId ,valueIds; List<Ssav>
        //  第二种解决方案：map 来代替  map.put("skuId","38") ;  ssav.setSkuId("38")
        //  map 中的key 就是实体类的属性！
        /*
            class Ssav{
                private Long skuId;
                private String valueIds;
            }

            a.字段相对较少，
            b.不是频繁被使用的时候。
         */
        //   List<Ssav> mapList = skuSaleAttrValueMapper.selectSkuValueIdsMap(spuId);
        List<Map> mapList = skuSaleAttrValueMapper.selectSaleAttrValuesBySpu(spuId);
        if(!CollectionUtils.isEmpty(mapList)){
            for (Map map : mapList) {
                hashMap.put(map.get("value_ids"),map.get("sku_id"));
            }
        }
        return hashMap;
    }

}
