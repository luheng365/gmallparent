package com.atguigu.gmall.payment.service.impl;

import com.alibaba.fastjson.JSON;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.request.AlipayTradeCloseRequest;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradeCloseResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.client.OrderFeignClient;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;

/**
 * @author luheng
 * @create 2020-12-21 21:09
 * @param:
 */
@Service
public class AlipayServiceImpl implements AlipayService {


    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private AlipayClient alipayClient;

    @Override
    public String createaliPay(Long orderId) throws AlipayApiException {
        //  调用一个保存交易记录的方法。
        //  根据Orderid 查询orderInfo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //判断订单的状态确定是否继续执行，生成二维码
        if("ClOSED".equals(orderInfo.getOrderStatus())||"PAID".equals(orderInfo.getOrderStatus())){

            return "订单已经支付或订单已经关闭";
        }

        paymentService.savePaymentInfo(orderInfo, PaymentType.ALIPAY);
        //  生成二维码。
        //  修改数据  alipayClient 操作支付宝所有接口的关键类。 可以将这个AlipayClient 注入到spring容器
        //  AlipayClient alipayClient =  new DefaultAlipayClient( "https://openapi.alipay.com/gateway.do" , APP_ID, APP_PRIVATE_KEY, FORMAT, CHARSET, ALIPAY_PUBLIC_KEY, SIGN_TYPE);  //获得初始化的AlipayClient
        AlipayTradePagePayRequest alipayRequest =  new  AlipayTradePagePayRequest(); //创建API对应的request
        alipayRequest.setReturnUrl(AlipayConfig.return_payment_url);
        alipayRequest.setNotifyUrl(AlipayConfig.notify_payment_url); //在公共参数中设置回跳和通知地址
        //  封装的业务参数
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("product_code","FAST_INSTANT_TRADE_PAY");
        //  map.put("total_amount",orderInfo.getTotalAmount());
        map.put("timeout_express","10m");
        map.put("total_amount","0.01");
        map.put("subject",orderInfo.getTradeBody());
        //  map.put("time_expire",""); 2016-12-31 10:05:01
        alipayRequest.setBizContent(JSON.toJSONString(map));
        //        alipayRequest.setBizContent( "{"  +
        //                "    \"out_trade_no\":\"\","  +
        //                "    \"product_code\":\"FAST_INSTANT_TRADE_PAY\","  +
        //                "    \"total_amount\":88.88,"  +
        //                "    \"subject\":\"Iphone6 16G\","  +
        //                "    \"body\":\"Iphone6 16G\","  +
        //                "    \"passback_params\":\"merchantBizType%3d3C%26merchantBizNo%3d2016010101111\","  +
        //                "    \"extend_params\":{"  +
        //                "    \"sys_service_provider_id\":\"2088511833207846\""  +
        //                "    }" +
        //                "  }" ); //填充业务参数
        //  返回表单的数据字符串。 在控制器上，可以直接写一个@ResponseBody 注解直接将这个字符串显示到页面。
        return alipayClient.pageExecute(alipayRequest).getBody();  //调用SDK生成表单
    }

    @Override
    public boolean refund(Long orderId) {
        //  根据orderid 获取orderInfo
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        //  退款实现
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("refund_amount","0.01");
        map.put("refund_reason","太贵了...");
        request.setBizContent(JSON.toJSONString(map));

        AlipayTradeRefundResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            //  修改本地订单状态，修改交易记录，关闭支付宝的交易.....
            PaymentInfo paymentInfo = new PaymentInfo();
            paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());
            paymentService.updatePaymentInfo(orderInfo.getOutTradeNo(),PaymentType.ALIPAY.name(),paymentInfo);
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }


    /***
     * 关闭交易
     * @param orderId
     * @return
     */
    @Override
    public Boolean closePay(Long orderId) {
        //  通过订单Id 获取订单对象
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);

        AlipayTradeCloseRequest request = new AlipayTradeCloseRequest();
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());
        map.put("operator_id","YX01");
        request.setBizContent(JSON.toJSONString(map));

        AlipayTradeCloseResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }

    /**
     * 根据订单查询是否支付成功！
     * @param orderId
     * @return
     */
    @Override
    public Boolean checkPayment(Long orderId) {
        //  通过订单Id 获取订单对象
        OrderInfo orderInfo = orderFeignClient.getOrderInfo(orderId);
        HashMap<String, Object> map = new HashMap<>();
        map.put("out_trade_no",orderInfo.getOutTradeNo());

        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent(JSON.toJSONString(map));

        AlipayTradeQueryResponse response = null;
        try {
            response = alipayClient.execute(request);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        if(response.isSuccess()){
            System.out.println("调用成功");
            return true;
        } else {
            System.out.println("调用失败");
            return false;
        }
    }

}
