package com.atguigu.gmall.order.service;

import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;
import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-18 19:38
 * @param:
 */
public interface OrderService extends IService<OrderInfo> {

    /**
     * 保存订单
     * @param orderInfo
     * @return
     */
    Long saveOrderInfo(OrderInfo orderInfo);

    /**
     * 生产流水号
     * @param userId
     * @return
     */
    String getTradeNo(String userId);

    /**
     * 比较流水号
     * @param userId 获取缓存中的流水号
     * @param tradeCodeNo   页面传递过来的流水号
     * @return
     */
    boolean checkTradeNo(String tradeCodeNo,String userId);


    /**
     * 删除流水号
     * @param userId
     */
    void deleteTradeNo(String userId);
    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    boolean checkStock(Long skuId, Integer skuNum);

    /**根据订单id 关闭订单
     * 处理过期订单
     * @param orderId
     */
    void execExpiredOrder(Long orderId);

    //  更新订单状态
    void updateOrderStatus(Long orderId, ProcessStatus processStatus);



    /**
     * 根据订单Id 查询订单信息
     * @param orderId
     * @return
     */
    OrderInfo getOrderInfo(Long orderId);
    /**
     * 发送消息给库存！
     * @param orderId
     */
    void sendOrderStatus(Long orderId);

    //定义一个方法将orderInfo转换成map
    Map initWareOrder(OrderInfo orderInfo);

    /**
     * 根据orderId，wareSkuMap 获取子订单集合
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    List<OrderInfo> orderSplit(String orderId, String wareSkuMap);

    /**
    * 取消订单业务【关闭orderInfo。paymentInfo】
    * @param orderId
     * @param flag
     */
    void execExpiredOrder(Long orderId, String flag);
}
