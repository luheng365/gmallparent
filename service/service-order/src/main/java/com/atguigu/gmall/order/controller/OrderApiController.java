package com.atguigu.gmall.order.controller;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.cart.client.CartFeignClient;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.AuthContextHolder;
import com.atguigu.gmall.model.cart.CartInfo;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.product.SkuInfo;
import com.atguigu.gmall.model.user.UserAddress;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.product.client.ProductFeignClient;
import com.atguigu.gmall.user.client.UserFeignClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author luheng
 * @create 2020-12-18 18:11
 * @param:
 */
@RestController
@RequestMapping("api/order")
public class OrderApiController {

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ThreadPoolExecutor threadPoolExecutor;

    @Autowired
    private RabbitService rabbitService;

    //  展示订单页面数据 送货地址列表，送货清单等数据。
    //  带有auth 就意味着必须登录，在网关中认证了。
    @GetMapping("auth/trade")
    public Result<Map<String,Object>> trade(HttpServletRequest request){
        //  获取用户Id
        String userId = AuthContextHolder.getUserId(request);

        //  根据用户Id 获取收货地址列表
        List<UserAddress> userAddressList = userFeignClient.findUserAddressListByUserId(userId);
        //  获取送货清单 {orderDetail}
        List<CartInfo> cartCheckedList = cartFeignClient.getCartCheckedList(userId);

        //  定义一个变量存储总件数
        int totalNum = 0;
        //  声明一个集合来存储订单明细
        List<OrderDetail> detailArrayList = new ArrayList<>();
        //  将cartInfo 数据赋值给orderDetail
        for (CartInfo cartInfo : cartCheckedList) {
            OrderDetail orderDetail = new OrderDetail();
            orderDetail.setSkuId(cartInfo.getSkuId());
            orderDetail.setSkuName(cartInfo.getSkuName());
            orderDetail.setImgUrl(cartInfo.getImgUrl());
            orderDetail.setOrderPrice(cartInfo.getSkuPrice());
            orderDetail.setSkuNum(cartInfo.getSkuNum());
            //  赋值总件数
            totalNum+= orderDetail.getSkuNum();
            detailArrayList.add(orderDetail);
        }

        //  计算总价格
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderDetailList(detailArrayList);
        //  计算的总价格：
        orderInfo.sumTotalAmount();
        HashMap<String, Object> map = new HashMap<>();

        //  页面需要的数据： ${userAddressList}  ${detailArrayList} ${totalNum} ${totalAmount}
        //  获取流水号
        String tradeNo = orderService.getTradeNo(userId);
        //  ${tradeNo}
        map.put("tradeNo",tradeNo);
        map.put("userAddressList",userAddressList);
        map.put("detailArrayList",detailArrayList);
        //  map.put("totalNum",detailArrayList.size());
        map.put("totalNum",totalNum);
        map.put("totalAmount",orderInfo.getTotalAmount());
        return Result.ok(map);
    }

    //  下订单数据保存
    //  http://api.gmall.com/api/order/auth/submitOrder?tradeNo=null
    @PostMapping("auth/submitOrder")
    public Result submitOrder(@RequestBody OrderInfo orderInfo, HttpServletRequest request){
        //  获取用户Id
        String userId = AuthContextHolder.getUserId(request);
        orderInfo.setUserId(Long.parseLong(userId));

        //  获取tradeNo
        String tradeNo = request.getParameter("tradeNo");
        //  比较
        boolean flag = orderService.checkTradeNo(tradeNo, userId);
        //        if (flag){
        //            //  提交
        //        }else{
        //            //  不能提交
        //        }
        //  判断比较结果
        if (!flag){
            //  不能提交
            return Result.fail().message("不可以重复提交...");
        }
        /*
            1.  异步编排应该是一个集合
            2.  将每个异步编排的执行结果记录在一个集合中。
                这个集合中只要有一个msg。那么就下订单失败。

         */

        List<String> errorList = new ArrayList<>();
        //  记录每个CompletableFuture
        List<CompletableFuture> futureList = new ArrayList<>();
        //  获取订单明细集合
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        //  订单明细中有5条商品
        for (OrderDetail orderDetail : orderDetailList) {
            //  验证库存
            CompletableFuture<Void> checkStockCompletableFuture = CompletableFuture.runAsync(() -> {
                //  每个商品都必须要验证：
                boolean res = orderService.checkStock(orderDetail.getSkuId(), orderDetail.getSkuNum());
                //  判断结果
                if (!res) {
                    //  验证库存失败
                    errorList.add(orderDetail.getSkuName() + "库存不足！");
                }
            },threadPoolExecutor);

            //  将 checkStockCompletableFuture 放入 futureList 集合中。
            futureList.add(checkStockCompletableFuture);

            //  验证价格
            CompletableFuture<Void> checkPriceCompletableFuture = CompletableFuture.runAsync(() -> {
                // 获取商品的实时价格
                //  select * from sku_info ; select * from sku_image where skuId = skuId;
                //  SkuInfo skuInfo = productFeignClient.getSkuInfo(orderDetail.getSkuId());
                //  select price from sku_info where id = skuId;
                BigDecimal skuPrice = productFeignClient.getSkuPrice(orderDetail.getSkuId());
                //  判断是否一致
                if (orderDetail.getOrderPrice().compareTo(skuPrice)!=0){
                    //  价格有变动
                    //  价格有变动了，更新一下价格。
                    cartFeignClient.loadCartCache(userId);
                    errorList.add(orderDetail.getSkuName()+"价格有变动.");
                }
            },threadPoolExecutor);
            //  将 checkPriceCompletableFuture 放入 futureList 集合中。
            futureList.add(checkPriceCompletableFuture);
        }
        //  需要将所有的CompletableFuture 添加进来。
        //        for (CompletableFuture completableFuture : futureList) {
        //            //  添加进来。
        //            CompletableFuture.allOf(completableFuture).join();
        //        }
        //        int a[] = new int[10];
        //  用集合来记录所有的异步编排数据对象，然后使用allOf 连接。
        CompletableFuture.allOf(futureList.toArray(new CompletableFuture[futureList.size()])).join();

        //  判断结果集
        if(errorList.size()>0){
            //  获取 errorList 集合中的数据
            //  表示将 errorList 集合中的每个元素以，拼接在一起。
            return Result.fail().message(StringUtils.join(errorList,","));
        }

        //  删除缓存的流水号
        orderService.deleteTradeNo(userId);
        //  返回订单Id
        Long orderId = orderService.saveOrderInfo(orderInfo);

        //  发送消息：内容是什么?
        rabbitService.sendDelayMessage(MqConst.EXCHANGE_DIRECT_ORDER_CANCEL,MqConst.ROUTING_ORDER_CANCEL,orderId,MqConst.DELAY_TIME);
        return Result.ok(orderId);
    }

    /**
     * 内部调用获取订单
     * @param orderId
     * @return
     */
    @GetMapping("inner/getOrderInfo/{orderId}")
    public OrderInfo getOrderInfo(@PathVariable Long orderId){
        return orderService.getOrderInfo(orderId);
    }

    /**
     * 拆单接口
     * http://localhost:8204/api/order/orderSplit
     * @param request
     * @return
     */
    @PostMapping("orderSplit")
    @ResponseBody
    public String orderSplit(HttpServletRequest request){


        //获取传递过来的参数
        String orderId = request.getParameter("orderId");
        //[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        String wareSkuMap = request.getParameter("wareSkuMap");

        //拆单之后返回的数据是什么
        //子订单的本质也是orderInfo
        List<OrderInfo>  subOrderInfoList = orderService.orderSplit(orderId,wareSkuMap);

        //声明一个map集合
        ArrayList<Map> maps = new ArrayList<>();

        //遍历子订单集合
        for (OrderInfo orderInfo : subOrderInfoList) {
            //orderInfo变成map
            Map map = orderService.initWareOrder(orderInfo);
            maps.add(map);
        }

        return JSON.toJSONString(maps);
    }

    /**
     * 秒杀提交订单，秒杀订单不需要做前置判断，直接下单
     * @param orderInfo
     * @return
     */
    @PostMapping("inner/seckill/submitOrder")
    public Long submitOrder(@RequestBody OrderInfo orderInfo){
        Long orderId = orderService.saveOrderInfo(orderInfo);
        //  返回当前的订单Id
        return orderId;
    }




}
