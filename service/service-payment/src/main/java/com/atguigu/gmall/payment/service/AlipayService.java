package com.atguigu.gmall.payment.service;

import com.alipay.api.AlipayApiException;

/**
 * @author luheng
 * @create 2020-12-21 21:07
 * @param:
 */
public interface AlipayService {

    /**
     * 支付的接口
     * @param orderId
     * @return
     * @throws AlipayApiException
     */
    String createaliPay(Long orderId) throws AlipayApiException;

    /**
     * 退款接口
     * @param orderId
     * @return
     */
    boolean refund(Long orderId);

    /***
     * 关闭交易
     * @param orderId
     * @return
     */
    Boolean closePay(Long orderId);

    /**
     * 根据订单查询是否支付成功！
     * @param orderId
     * @return
     */
    Boolean checkPayment(Long orderId);

}
