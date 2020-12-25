package com.atguigu.gmall.payment.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.atguigu.gmall.common.result.Result;
import com.atguigu.gmall.model.enums.PaymentType;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.payment.config.AlipayConfig;
import com.atguigu.gmall.payment.service.AlipayService;
import com.atguigu.gmall.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-21 21:21
 * @param:
 */
@Controller
@RequestMapping("/api/payment/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PaymentService paymentService;

    //接写一个@ResponseBody 注解直接将这个字符串显示到页面。
    @RequestMapping("submit/{orderId}")
    @ResponseBody
    public String submitAlipay(@PathVariable Long orderId){
        String from = "";
        try {
            //  调用方法即可。
            from = alipayService.createaliPay(orderId);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        return from;
    }

    //http://api.gmall.com/api/payment/alipay/callback/return
    @RequestMapping("callback/return")
    public String callbackPayment(){
        //  http://payment.gmall.com/pay/success.html 重定向
        return "redirect:"+ AlipayConfig.return_order_url;
    }

    //  异步回调：http://v3xcpn.natappfree.cc/api/payment/alipay/callback/notify
    //  https: //商家网站通知地址?voucher_detail_list=[{"amount":"0.20","merchantContribute":"0.00","name":"5折券","otherContribute":"0.20","type":"ALIPAY_DISCOUNT_VOUCHER","voucherId":"2016101200073002586200003BQ4"}]&fund_bill_list=[{"amount":"0.80","fundChannel":"ALIPAYACCOUNT"},{"amount":"0.20","fundChannel":"MDISCOUNT"}]&subject=PC网站支付交易&trade_no=2016101221001004580200203978&gmt_create=2016-10-12 21:36:12&notify_type=trade_status_sync&total_amount=1.00&out_trade_no=mobile_rdm862016-10-12213600&invoice_amount=0.80&seller_id=2088201909970555&notify_time=2016-10-12 21:41:23&trade_status=TRADE_SUCCESS&gmt_payment=2016-10-12 21:37:19&receipt_amount=0.80&passback_params=passback_params123&buyer_id=2088102114562585&app_id=2016092101248425&notify_id=7676a2e1e4e737cff30015c4b7b55e3kh6& sign_type=RSA2&buyer_pay_amount=0.80&sign=***&point_amount=0.00
    //  springMVC
    @RequestMapping("callback/notify")
    @ResponseBody
    public String callbackNotify(@RequestParam Map<String, String> paramMap){
        System.out.println("来人了，开始接客了。。。。。");
        //  验签 ，成功返回success.
        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, AlipayConfig.alipay_public_key, AlipayConfig.charset, AlipayConfig.sign_type);
        } catch (AlipayApiException e) {
            e.printStackTrace();
        }
        //  获取支付宝传递的参数
        String outTradeNo = paramMap.get("out_trade_no");

        //  获取交易状态
        String tradeStatus = paramMap.get("trade_status");
        //  根据这个参数查询交易记录，如果能够得到，说明一致！
        // 验证out_trade_no
        PaymentInfo paymentInfo =  paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (paymentInfo==null){
            //  验证参数失败.....
            return "failure";
        }
        //  totamout,app_id,
        //  判断是否验签成功
        if(signVerified){
            // TODO 验签成功后，按照支付结果异步通知中的描述，对支付结果中的业务内容进行二次校验，校验成功后在response中返回success并继续商户自身业务处理，校验失败返回failure
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)){

                //  细节处理：需要做一个判断：这个交易记录不能是关闭状态，或者是支付状态！
                if (paymentInfo.getPaymentStatus().equals("ClOSED") ||
                        paymentInfo.getPaymentStatus().equals("PAID")){
                    return "failure";
                }
                //  更新一下交易记录状态
                paymentService.paySuccess(outTradeNo, PaymentType.ALIPAY.name(),paramMap);

                //  返回支付成功
                return "success";
            }
        }else{
            // TODO 验签失败则记录异常日志，并在response中返回failure.
        }

        //  失败，failure
        return "success";
    }

    //  退款
    @RequestMapping("refund/{orderId}")
    @ResponseBody
    public Result refund (@PathVariable Long orderId){
        //  调用退款方法
        boolean flag = alipayService.refund(orderId);
        return Result.ok(flag);
    }

    //http://localhost:8205/api/payment/alipay/closePay/25
    // 根据订单Id关闭订单
    @GetMapping("closePay/{orderId}")
    @ResponseBody
    public Boolean closePay(@PathVariable Long orderId){
        Boolean aBoolean = alipayService.closePay(orderId);
        return aBoolean;
    }
    /**
     * 根据订单查询是否支付成功！
     * @param orderId
     * @return
     */
    // 查看是否有交易记录
    @RequestMapping("checkPayment/{orderId}")
    @ResponseBody
    public Boolean checkPayment(@PathVariable Long orderId){
        // 调用退款接口
        boolean flag = alipayService.checkPayment(orderId);
        return flag;
    }

    /**
     * 根据第三方编号和支付类型查询paymentInfo
     * @param outTradeNo
     * @return
     */
    @GetMapping("getPaymentInfo/{outTradeNo}")
    @ResponseBody
    public PaymentInfo getPaymentInfo(@PathVariable String outTradeNo){
        PaymentInfo paymentInfo = paymentService.getPaymentInfo(outTradeNo, PaymentType.ALIPAY.name());
        if (null!=paymentInfo){
            return paymentInfo;
        }
        return null;
    }
}
