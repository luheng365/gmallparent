package com.atguigu.gmall.cart.service.impl;

import com.atguigu.gmall.cart.mapper.CartInfoMapper;
import com.atguigu.gmall.cart.service.CartAsyncService;
import com.atguigu.gmall.cart.service.CartService;
import com.atguigu.gmall.common.constant.RedisConst;
import com.atguigu.gmall.common.util.DateUtil;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author luheng
 * @create 2020-12-17 14:51
 * @param:
 */
@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private CartInfoMapper cartInfoMapper;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private CartAsyncService cartAsyncService;
    @Override
    public void addToCart(Long skuId, String userId, Integer skuNum) {
        /*
            1.  添加商品之前，先看一下购物车中是否有该商品
                true:
                    商品数量相加
                false:
                    直接加入购物车
            2.    将数据同步到redis！

         */
        //  数据类型hash + key hset(key,field,value)
        //  key = user:userId:cart ,谁的购物车 field = skuId value = cartInfo
        String cartKey = this.getCartKey(userId);
        //  判断缓存中是否有数据
        if (!redisTemplate.hasKey(cartKey)){
            //  加载数据库并放入缓存
            this.loadCartCache(userId);
        }
        //  select * from cart_info where skuId = ? and userId = ?;
        //  修改表
        //        QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
        //        queryWrapper.eq("user_id",userId);
        //        queryWrapper.eq("sku_id",skuId);
        //        CartInfo cartInfoExist = cartInfoMapper.selectOne(queryWrapper);
        //  查询缓存 hget(key,field);
        CartInfo cartInfoExist = (CartInfo) redisTemplate.boundHashOps(cartKey).get(skuId.toString());

        //  当前购物车中有该商品
        if (cartInfoExist!=null){
            //  数量相加
            cartInfoExist.setSkuNum(cartInfoExist.getSkuNum()+skuNum);
            //  初始化实时价格
            //  本质skuPrice = skuInfo.price
            cartInfoExist.setSkuPrice(productFeignClient.getSkuPrice(skuId));

            //  可选！
            cartInfoExist.setIsChecked(1);

            //  修改更新时间
            cartInfoExist.setUpdateTime(new Timestamp(new Date().getTime()));

            //  修改数据库执行语句
            // cartInfoMapper.updateById(cartInfoExist);
            cartAsyncService.updateCartInfo(cartInfoExist);
            //  修改缓存
            //  redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        }else {
            //  第一次添加购物车
            CartInfo cartInfo = new CartInfo();
            SkuInfo skuInfo = productFeignClient.getSkuInfo(skuId);
            cartInfo.setUserId(userId);
            cartInfo.setSkuId(skuId);
            //  在初始化的时候，添加购物车的价格 = skuInfo.price
            cartInfo.setCartPrice(skuInfo.getPrice());
            //  数据库不存在的，购物车的价格 = skuInfo.price
            cartInfo.setSkuPrice(skuInfo.getPrice());
            cartInfo.setSkuNum(skuNum);
            cartInfo.setImgUrl(skuInfo.getSkuDefaultImg());
            cartInfo.setSkuName(skuInfo.getSkuName());
            cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
            cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
            //  执行数据库操作
            //  cartInfoMapper.insert(cartInfo);
            cartAsyncService.saveCartInfo(cartInfo);

            //  放入缓存
            //  redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfo);
            cartInfoExist = cartInfo;
        }

        //  存储数据 hset(key,field,value);
        //  redisTemplate.opsForHash().put(cartKey,skuId.toString(),cartInfoExist);
        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfoExist);
        //  设置过期时间
        this.setCartKeyExpire(cartKey);
    }

    @Override
    public List<CartInfo> getCartList(String userId, String userTempId) {
        List<CartInfo> cartInfoList = new ArrayList<>();

        //  判断登录的用户Id 为空，则按照临时用户Id 查询
        //  userId=null , 保证一定能够获取到的userTempId 就不为空么?
        if (StringUtils.isEmpty(userId)){
            cartInfoList = getCartList(userTempId);
        }

        //  判断登录的用户Id 不为空，则按照用户Id 查询
        if (!StringUtils.isEmpty(userId)){
            //  可能发生合并，查看未登录购物车数据
            if (StringUtils.isEmpty(userTempId)){
                //  直接返回 cartInfoNoLoginList = null
                cartInfoList = getCartList(userId);
            }else {
                List<CartInfo> cartInfoNoLoginList = getCartList(userTempId);
                if (!CollectionUtils.isEmpty(cartInfoNoLoginList)){
                    //  合并！
                    cartInfoList = this.mergeToCartList(cartInfoNoLoginList,userId);
                    //  删除未登录购物车数据
                    this.deleteCartList(userTempId);
                }else {
                    //  直接返回 cartInfoNoLoginList = null
                    cartInfoList = getCartList(userId);
                }
            }
        }
        return cartInfoList;
    }

    @Override
    public void checkCart(String userId, Integer isChecked, Long skuId) {
        //  更新数据库
        cartAsyncService.checkCart(userId,isChecked,skuId);

        //  更新缓存    dml 操作，可以先删除缓存，然后重新加载。
        //  先获取数据
        String cartKey = this.getCartKey(userId);
        //  从缓存中获取cartInfo
//        CartInfo cartInfo = (CartInfo) redisTemplate.boundHashOps(cartKey).get(skuId.toString());
        //  判断这个是否存在。
        if (redisTemplate.hasKey(cartKey)){
            //  判断这个key 是否存在。
            BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
            if (boundHashOperations.hasKey(skuId.toString())){
                //  如果有这个key，则获取对象
                CartInfo cartInfo = (CartInfo) boundHashOperations.get(skuId.toString());
                //  修改数据
                cartInfo.setIsChecked(isChecked);

                //  放入这个缓存
                redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfo);
            }
        }

//        //  修改数据
//        cartInfo.setIsChecked(isChecked);
//
//        //  放入这个缓存
//        redisTemplate.boundHashOps(cartKey).put(skuId.toString(),cartInfo);
        //  从新设置一下过期时间
        //  灵活 --- this.setCartKeyExpire(cartKey);
    }

    @Override
    public void deleteCartInfo(String userId, Long skuId) {
        //  异步数据库
        cartAsyncService.deleteCartInfo(userId,skuId);

        //  删除缓存,先获取key
        String cartKey = this.getCartKey(userId);

        //  判断有没有这个key
        if (redisTemplate.hasKey(cartKey)){
            BoundHashOperations boundHashOperations = redisTemplate.boundHashOps(cartKey);
            //  判断这个购物车中是否有这个商品
            if (boundHashOperations.hasKey(skuId.toString())){
                //  删除这个商品
                boundHashOperations.delete(skuId.toString());
            }
        }

    }

    /**
     * 根据用户Id 查询购物车列表
     * @param userId
     * @return
     */
    @Override
    public List<CartInfo> getCartCheckedList(String userId) {
        //  用户查看购物车列表了，那么认为缓存有数据了。
        String cartKey = this.getCartKey(userId);
        List<CartInfo> cartInfoCheckedList = new ArrayList<>();
        //  获取所有的数据
        List<CartInfo> cartInfoList = redisTemplate.boundHashOps(cartKey).values();

        //  循环遍历
        for (CartInfo cartInfo : cartInfoList) {
            //  获取选中状态的购物车
            if (cartInfo.getIsChecked().intValue()==1){
                cartInfoCheckedList.add(cartInfo);
            }
        }

        //        List<Object> collect = cartInfoList.stream().map((cartInfo -> {
        //            if (cartInfo.getIsChecked().intValue() == 1) {
        //                return cartInfo;
        //            }
        //            return null;
        //        })).collect(Collectors.toList());

        //        List<CartInfo> collect = cartInfoList.stream().filter(new Predicate<CartInfo>() {
        //            @Override
        //            public boolean test(CartInfo cartInfo) {
        //                //                if (cartInfo.getIsChecked().intValue() == 1) {
        //                //                    return true;
        //                //                }
        //                //                return false;
        //                return cartInfo.getIsChecked().intValue() == 1;
        //            }
        //        }).collect(Collectors.toList());

        //  return  collect;
        return cartInfoCheckedList;
    }

    /**
     * 删除临时购物车数据
     * @param userTempId
     */
    private void deleteCartList(String userTempId) {
        //  删除数据库 + 删除缓存
        CartInfo cartInfo = new CartInfo();
        cartInfo.setUserId(userTempId);
        cartAsyncService.deleteCartInfo(cartInfo);
        //  获取缓存的key
        String cartKey = this.getCartKey(userTempId);
        if (redisTemplate.hasKey(cartKey)){
            redisTemplate.delete(cartKey);
        }

    }

    /**
     * 合并购物车方法
     * @param cartInfoNoLoginList 未登录购物车数据
     * @param userId
     * @return
     */
    private List<CartInfo> mergeToCartList(List<CartInfo> cartInfoNoLoginList, String userId) {
         /*
        demo1:
            登录：
                37 1
                38 1
            未登录：
                37 1
                38 1
                39 1
            合并之后的数据
                37 2
                38 2
                39 1
         demo2 :
             未登录：
                37 1
                38 1
                39 1
              合并之后
                37 1
                38 1
                39 1
             */
        //  根据用户 Id 查询登录的购物车数据
        List<CartInfo> cartInfoLoginList = this.getCartList(userId);
        //  登录购物车数据要是空，则直接返回{需要将未登录的数据添加到数据库}
        //  if (CollectionUtils.isEmpty(cartInfoLoginList)) return cartInfoNoLoginList;
        //  登录的购物车数据不为空！ 合并条件skuId
        //  Function T R
        //  if(!CollectionUtils.isEmpty(cartInfoLoginList)){
        Map<Long, CartInfo> longCartInfoMap = cartInfoLoginList.stream().collect(Collectors.toMap(CartInfo::getSkuId, cartInfo -> cartInfo));
        //  判断未登录的skuId 在已登录的Map 中是否有这个key
        //  遍历集合
        for (CartInfo cartInfo : cartInfoNoLoginList) {
            Long skuId = cartInfo.getSkuId();
            //  skuId 是否存在  37,38
            if (longCartInfoMap.containsKey(skuId)){
                //  未登录购物车中的商品，在登录中也存在！数量相加
                //  用这个skuId 对应获取登录的cartInfo 对象
                CartInfo cartInfoLogin = longCartInfoMap.get(skuId);
                //  赋值商品数量
                cartInfoLogin.setSkuNum(cartInfoLogin.getSkuNum()+cartInfo.getSkuNum());
                //  更新一下cartInfo 的更新时间
                cartInfoLogin.setUpdateTime(new Timestamp(new Date().getTime()));

                //  合并购物车时的选择状态！
                //  以未登录选中状态为基准
                if (cartInfo.getIsChecked().intValue()==1){
                    cartInfoLogin.setIsChecked(1);
                }
                //  更新数据库 同步更新
                //  cartInfoMapper.updateById(cartInfoLogin);
                //  因为：合并的时候，缓存有数据的话，缓存中cartInfo.id 为null，使用updateById更新失败。
                //  使用 update cart_info set sku_num = ? where sku_id = ? and user_id = ?
                QueryWrapper<CartInfo> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id",cartInfoLogin.getUserId());
                queryWrapper.eq("sku_id",cartInfoLogin.getSkuId());
                //  cartInfo第一个参数相当于 ，第二个参数相当于更新条件
                cartInfoMapper.update(cartInfoLogin,queryWrapper);
                //  异步更新 代码不会走这个方法体！意味着不会更新数据库
                //  cartAsyncService.updateCartInfo(cartInfoLogin);

            }else {
                //  处理39
                //  赋值登录的用户id
                cartInfo.setUserId(userId);
                //  添加时间
                cartInfo.setCreateTime(new Timestamp(new Date().getTime()));
                cartInfo.setUpdateTime(new Timestamp(new Date().getTime()));
                cartInfoMapper.insert(cartInfo);
                //  代码不会走这个方法体！意味着不会更新数据库
                //  cartAsyncService.saveCartInfo(cartInfo);
            }
        }
        //  }
        //  从数据库中获取到最新合并的数据，然后放入缓存
        List<CartInfo> cartInfoList = this.loadCartCache(userId);
        //  返回数据
        return cartInfoList;
    }

    //  根据用户Id 查询购物车列表
    private List<CartInfo> getCartList(String userId) {

        String cartKey = this.getCartKey(userId);

        List<CartInfo> cartInfoList = new ArrayList<>();
        //  这个用户Id 可以代表登录，也可以代表未登录。
        //  判断临时用户Id 是否为空
        if(StringUtils.isEmpty(userId)){
            return cartInfoList;
        }
        //  查询的业务逻辑 先查询缓存，如果缓存没有，则查询数据
        //        if (redisTemplate.hasKey(cartKey)){
        //
        //        }
        //  获取缓存的数据，获取购物车中value的数据。 hvals(cartKey);
        cartInfoList = redisTemplate.boundHashOps(cartKey).values();
        //  cartInfoList = redisTemplate.opsForHash().values(cartKey);
        if (!CollectionUtils.isEmpty(cartInfoList)){
            //  Comparator 外部--自定义比较器
            //  Comparable 内部比较器
            //  查询的时候需要进行排序：修改时间
            cartInfoList.sort(new Comparator<CartInfo>() {
                @Override
                public int compare(CartInfo o1, CartInfo o2) {
                    //  o2.getUpdateTime().equals(o1.getUpdateTime())
                    return DateUtil.truncatedCompareTo(o2.getUpdateTime(),o1.getUpdateTime(), Calendar.SECOND);
                }
            });
            return cartInfoList;
        }else {
            //  缓存中没有数据，从数据库中获取，并放入缓存
            cartInfoList = this.loadCartCache(userId);

            return cartInfoList;
        }
    }
    //根据用户Id查询实时价格
    public List<CartInfo> loadCartCache(String userId) {
        //  从数据库中获取，并放入缓存
        //  select * from cart_info where user_id= ?
        List<CartInfo> cartInfoList = cartInfoMapper.selectList(new QueryWrapper<CartInfo>().eq("user_id", userId));
        //  判断
        if (CollectionUtils.isEmpty(cartInfoList)){
            return cartInfoList;
        }
        //  数据库中的数据不为空，
        //  获取key
        String cartKey = this.getCartKey(userId);
        //  放入缓存 hset(key,field,value); hmset(key,map);
        HashMap<String, Object> map = new HashMap<>();
        for (CartInfo cartInfo : cartInfoList) {
            //  因为在缓存中已经没有数据了，查询了一次数据库，有可能价格会发生变化
            cartInfo.setSkuPrice(productFeignClient.getSkuPrice(cartInfo.getSkuId()));
            map.put(cartInfo.getSkuId().toString(),cartInfo);
            //  redisTemplate.boundHashOps(cartKey).put(cartInfo.getSkuId().toString(),cartInfo);
        }
        //  存储完成
        redisTemplate.boundHashOps(cartKey).putAll(map);
        //  设置一个过期时间
        this.setCartKeyExpire(cartKey);
        //  返回数据
        return cartInfoList;
    }

    //  设置过期时间
    public void setCartKeyExpire(String cartKey) {
        redisTemplate.expire(cartKey, RedisConst.USER_CART_EXPIRE, TimeUnit.SECONDS);
    }

    //  封装一个获取购物车key 的方法
    public String getCartKey(String userId) {
        return RedisConst.USER_KEY_PREFIX+userId+RedisConst.USER_CART_KEY_SUFFIX;
    }
}
