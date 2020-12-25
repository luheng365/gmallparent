package com.atguigu.gmall.order.receiver;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.model.payment.PaymentInfo;
import com.atguigu.gmall.order.service.OrderService;
import com.atguigu.gmall.payment.client.PaymentFeignClient;
import com.rabbitmq.client.Channel;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * @author luheng
 * @create 2020-12-21 19:28
 * @param:
 */
@Component
public class OrderReceiver {

    @Autowired
    private OrderService orderService;

    @Autowired
    private PaymentFeignClient paymentFeignClient;

    @Autowired
    private RabbitService rabbitService;

    //  监听消息
    //  队列必须跟交换机去绑定。那么现在绑定关系有么?
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_ORDER_CANCEL)
    public void cancelOrder(Long orderId, Message message, Channel channel){
        //  取消订单业务。
        if (orderId!=null){
            //  查询是否有订单记录
            OrderInfo orderInfo = orderService.getById(orderId);
            //  判断 当前的状态
            if (orderInfo!=null && "UNPAID".equals(orderInfo.getOrderStatus()) &&
                    "UNPAID".equals(orderInfo.getProcessStatus())){
                //  判断在电商系统中是否有交易记录
                PaymentInfo paymentInfo = paymentFeignClient.getPaymentInfo(orderInfo.getOutTradeNo());
                //  判断paymentInfo 是否为空！ 如果是空
                if (paymentInfo!=null && "UNPAID".equals(paymentInfo.getPaymentStatus())){
                    //  说明用户点击了扫码支付,但是不能确定用户是否已经扫描了。
                    //  所以调用查询交易记录接口
                    Boolean flag = paymentFeignClient.checkPayment(orderId);
                    if (flag){
                        //  说明用户已经扫描了。
                        //  用户的到底有没有付款。
                        //  关闭支付宝的交易记录。
                        Boolean result = paymentFeignClient.closePay(orderId);
                        if (result){
                            //  true    扫描没有支付
                            //  说明用户没有扫描了。  关闭orderInfo，paymentInfo
                            orderService.execExpiredOrder(orderId,"2");
                        }else {
                            //  用户已经支付了。发送消息。
                            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_PAY,MqConst.ROUTING_PAYMENT_PAY,orderId);
                        }
                    }else {
                        //  说明用户没有扫描了。  关闭orderInfo，paymentInfo
                        orderService.execExpiredOrder(orderId,"2");
                    }
                }else {
                    //  用户没有点击扫描支付。只关闭orderInfo
                    orderService.execExpiredOrder(orderId,"1");
                }
            }

        }
        channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);

    }
    //监听支付是否成功
    @SneakyThrows
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = MqConst.QUEUE_PAYMENT_PAY,durable = "true",autoDelete = "false"),
            exchange = @Exchange(value = MqConst.EXCHANGE_DIRECT_PAYMENT_PAY),
            key = {MqConst.ROUTING_PAYMENT_PAY}
    ))
    public void paySuccess(Long orderId,Message message,Channel channel){
        //orderId不为空
        if(orderId!=null){
            //理想中认为orderInfo必有数据
            orderService.updateOrderStatus(orderId, ProcessStatus.PAID);
            //订单发送消息给库存
            orderService.sendOrderStatus(orderId);

            //消息确认
            channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
        }

    }
    //监听减库存结果的消息队列
    @SneakyThrows
    @RabbitListener(queues = MqConst.QUEUE_WARE_ORDER)
    public void updateOrderStatus(String msg,Message message,Channel channel){

        try {
            if(!StringUtils.isEmpty(msg)){
                //获取传递过来的数据 orderId  并转换为map
                Map map = JSON.parseObject(msg, Map.class);
                //判断减库存的状态
                String orderId = (String)map.get("orderId");
                String status = (String)map.get("status");

                if ("DEDUCTED".equals(status)){
                    //减库存成功
                    orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.WAITING_DELEVER);
                }else{
                    //减库存失败
                    orderService.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.STOCK_EXCEPTION);
                    //通知管理员。。。

                }
                //消息确认
                channel.basicAck(message.getMessageProperties().getDeliveryTag(),false);
            }
        } catch (NumberFormatException e) {
            channel.basicNack(message.getMessageProperties().getDeliveryTag(),false,false);
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
