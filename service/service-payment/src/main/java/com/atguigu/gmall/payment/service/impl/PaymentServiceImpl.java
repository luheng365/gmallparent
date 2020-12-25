package com.atguigu.gmall.payment.service.impl;

import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.PaymentStatus;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.mapper.PaymentInfoMapper;
import com.atguigu.gmall.payment.service.PaymentService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-21 20:55
 * @param:
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private RabbitService rabbitService;

    //保存支付交易记录 业务逻辑
    @Override
    public void savePaymentInfo(OrderInfo orderInfo, PaymentType paymentType) {

        //  在交易记录表中那么orderid，对应的支付方式只能有一条记录
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderInfo.getId());
        paymentInfoQueryWrapper.eq("payment_type",paymentType.name());
        //  paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        PaymentInfo paymentInfo1 = paymentInfoMapper.selectOne(paymentInfoQueryWrapper);
        //  当前表中有这条记录
        if (paymentInfo1!=null){
            return;
        }
        //  保存的业务逻辑。
        PaymentInfo paymentInfo = new PaymentInfo();
        //  赋值paymentInfo
        paymentInfo.setOutTradeNo(orderInfo.getOutTradeNo());
        paymentInfo.setOrderId(orderInfo.getId());
        paymentInfo.setPaymentType(paymentType.name());
        paymentInfo.setTotalAmount(orderInfo.getTotalAmount());
        paymentInfo.setSubject(orderInfo.getTradeBody());
        paymentInfo.setPaymentStatus(PaymentStatus.UNPAID.name());
        paymentInfo.setCreateTime(new Date());
        paymentInfoMapper.insert(paymentInfo);

    }

    @Override
    public PaymentInfo getPaymentInfo(String outTradeNo, String name) {
        //  select * from payment_info where out_trade_no = ? and payment_type = ?
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo);
        paymentInfoQueryWrapper.eq("payment_type",name);
        return paymentInfoMapper.selectOne(paymentInfoQueryWrapper);
    }

    @Override
    public void paySuccess(String outTradeNo, String name, Map<String, String> paramMap) {

        PaymentInfo paymentInfoQuery = this.getPaymentInfo(outTradeNo, name);
        if("PAID".equals(paymentInfoQuery.getPaymentStatus())||"ClOSED".equals(paymentInfoQuery.getPaymentStatus())){
            return;
        }

        //  update payment_info set trade_no=? ,payment_status = ? ,callback_time = ? where out_trade_no=? and payment_type = ?;
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setTradeNo(paramMap.get("trade_no"));
        paymentInfo.setCallbackTime(new Date());
        paymentInfo.setPaymentStatus(PaymentStatus.PAID.name());
        paymentInfo.setCallbackContent(paramMap.toString());

        //        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        //        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo);
        //        paymentInfoQueryWrapper.eq("payment_type",name);
        // 第一个参数表示啥?要更新的内容  第二个参数表示啥？
        //  paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,paymentInfoQuery.getOrderId());
        this.updatePaymentInfo(outTradeNo,name,paymentInfo);
    }
    /**
     * 方法复用
     * @param outTradeNo
     * @param name
     * @param paymentInfo
     */
    public void updatePaymentInfo(String outTradeNo, String name, PaymentInfo paymentInfo) {
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("out_trade_no",outTradeNo);
        paymentInfoQueryWrapper.eq("payment_type",name);
        // 第一个参数表示啥?要更新的内容  第二个参数表示啥？
        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);
    }

    /**
     * 根据订单id关闭paymentInfo
     * @param orderId
     */
    @Override
    public void closePayment(Long orderId) {

        //  paymentInfo 中什么时候会产生数据?
        //  点击扫码支付的时候。如果不点击扫码支付paymentInfo 会有数据么?
        QueryWrapper<PaymentInfo> paymentInfoQueryWrapper = new QueryWrapper<>();
        paymentInfoQueryWrapper.eq("order_id",orderId);
        paymentInfoQueryWrapper.eq("payment_type",PaymentType.ALIPAY.name());
        //  判断当前paymentInfo 中是否有数据
        Integer count = paymentInfoMapper.selectCount(paymentInfoQueryWrapper);
        if (count==0){
            return;
        }
        //  update payment_info set payment_status=close where order_id = ? and payment_type = ?
        PaymentInfo paymentInfo = new PaymentInfo();
        paymentInfo.setPaymentStatus(PaymentStatus.ClOSED.name());

        paymentInfoMapper.update(paymentInfo,paymentInfoQueryWrapper);

    }
}
