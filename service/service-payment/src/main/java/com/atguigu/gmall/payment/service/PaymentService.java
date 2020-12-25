package com.atguigu.gmall.payment.service;

import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;

import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-21 20:53
 * @param:
 */
//保存支付交易记录
public interface PaymentService {
    /**
     * 保存支付交易记录
     * @param orderInfo 通过订单Id 获取
     * @param paymentType 支付交易类型
     */
    void savePaymentInfo(OrderInfo orderInfo, PaymentType paymentType);

    /**
     * 根据第三方交易编号，支付类型查询交易记录
     * @param outTradeNo
     * @param name
     * @return
     */
    PaymentInfo getPaymentInfo(String outTradeNo, String name);

    /**
     * 根据第三方交易编号，支付类型更新交易状态
     * @param outTradeNo
     * @param name
     * @param paramMap
     */
    void paySuccess(String outTradeNo, String name, Map<String, String> paramMap);

    /**
     * 根据outTradeNo,支付类型，更新对象。
     * @param outTradeNo
     * @param name
     * @param paymentInfo
     */
    void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo);

    /**
     * 根据订单id关闭paymentInfo
     * @param orderId
     */
    void closePayment(Long orderId);
}
