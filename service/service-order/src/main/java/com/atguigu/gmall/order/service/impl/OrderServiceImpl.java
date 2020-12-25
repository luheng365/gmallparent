package com.atguigu.gmall.order.service.impl;

import com.alibaba.fastjson.JSON;
import com.atguigu.gmall.common.constant.MqConst;
import com.atguigu.gmall.common.service.RabbitService;
import com.atguigu.gmall.common.util.HttpClientUtil;
import com.atguigu.gmall.model.enums.OrderStatus;
import com.atguigu.gmall.model.enums.ProcessStatus;
import com.atguigu.gmall.model.order.OrderDetail;
import com.atguigu.gmall.model.order.OrderInfo;
import com.atguigu.gmall.order.mapper.OrderDetailMapper;
import com.atguigu.gmall.order.mapper.OrderInfoMapper;
import com.atguigu.gmall.order.service.OrderService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * @author luheng
 * @create 2020-12-18 19:40
 * @param:
 */
@Service
public class OrderServiceImpl extends ServiceImpl<OrderInfoMapper,OrderInfo>  implements OrderService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private RabbitService rabbitService;

    @Override
    @Transactional
    public Long saveOrderInfo(OrderInfo orderInfo) {

        //总金额 订单状态 userId out_trade_no 第三方交易编号 .....
        orderInfo.sumTotalAmount();
        orderInfo.setOrderStatus(OrderStatus.UNPAID.name());
        //orderInfo.setUserId();  //在控制器获取
        String outTradeNo = "ATGUIGU" + System.currentTimeMillis() + "" + new Random().nextInt(1000);
        orderInfo.setOutTradeNo(outTradeNo);
        //订单的描述
        orderInfo.setTradeBody("马上结束了");

        orderInfo.setCreateTime(new Date());
        //过期时间默认24小时
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE,1);
        orderInfo.setExpireTime(calendar.getTime());

        //进程状态
        orderInfo.setProcessStatus(ProcessStatus.UNPAID.name());

        //保存订单
        orderInfoMapper.insert(orderInfo);

        //保存订单明细
        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();

        for (OrderDetail orderDetail : orderDetailList) {
            orderDetail.setOrderId(orderInfo.getId());
            orderDetailMapper.insert(orderDetail);

        }
        //返回订单Id
        return orderInfo.getId();
    }

    //生产流水号
    @Override
    public String getTradeNo(String userId) {
        //声明流水号
        String tradeNO = UUID.randomUUID().toString();
        //放入缓存
        String tradeNoKey = "tradeCode" +userId;
        redisTemplate.opsForValue().set(tradeNoKey,tradeNO);

        return tradeNO;
    }
    //比较流水号
    @Override
    public boolean checkTradeNo(String tradeCodeNo, String userId) {
        //先获取缓存的
        String tradeNoKey = "tradeCode" +userId;
        String tradeNoRedis = (String) redisTemplate.opsForValue().get(tradeNoKey);

        return tradeCodeNo.equals(tradeNoRedis);
    }
    //删除流水号
    @Override
    public void deleteTradeNo(String userId) {
        String tradeNoKey = "tradeCode" +userId; // 定义key
        redisTemplate.delete(tradeNoKey);    //删除key
    }

    /**
     * 验证库存
     * @param skuId
     * @param skuNum
     * @return
     */
    @Override
    public boolean checkStock(Long skuId, Integer skuNum) {
        //远程调用
        //http://localhost:9001/hasStock
        ///hasStock?skuId=10221&num=2
        String res = HttpClientUtil.doGet("http://localhost:9001/hasStock?skuId=" + skuId + "&num=" + skuNum);
        //判断返回结果1 有库存
        return "1".equals(res);
    }

    /**根据订单id 关闭订单
     * 处理过期订单
     * @param orderId
     */
    @Override
    public void execExpiredOrder(Long orderId) {
        //    update order_info set order_status= ? process_status=? where id = ?;
        //        OrderInfo orderInfo = new OrderInfo();
        //        orderInfo.setId(orderId);
        //        orderInfo.setOrderStatus(OrderStatus.CLOSED.name());
        //        orderInfo.setProcessStatus(ProcessStatus.CLOSED.name());
        //        orderInfoMapper.updateById(orderInfo);

        //  进度中能够获取到订单状态
        //  后续会有很多地方，都会使用到根据进度状态来更新订单。
        updateOrderStatus(orderId,ProcessStatus.CLOSED);
        //发送一个消息关闭电商本地的交易记录
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);

    }

    public void updateOrderStatus(Long orderId, ProcessStatus processStatus) {
        //  update order_info set order_status= ? process_status=? where id = ?;
        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setId(orderId);
        orderInfo.setOrderStatus(processStatus.getOrderStatus().name());
        orderInfo.setProcessStatus(processStatus.name());
        orderInfoMapper.updateById(orderInfo);
    }



    /**
     * 根据订单Id 查询订单信息【查询订单明细】
     * @param orderId
     * @return
     */
    @Override
    public OrderInfo getOrderInfo(Long orderId) {
        //查询订单明细
        OrderInfo orderInfo = orderInfoMapper.selectById(orderId);
        if(orderInfo!=null){
            orderInfo.setOrderDetailList(orderDetailMapper.selectList(new QueryWrapper<OrderDetail>().eq("order_id",orderId)));
        }
        return orderInfo;
    }
    /**
     * 发送消息给库存！
     * @param orderId
     */
    @Override
    public void sendOrderStatus(Long orderId) {
        //改变订单的状态
        updateOrderStatus(orderId,ProcessStatus.NOTIFIED_WARE);


        //先获取到发送消息的数据，
        //获取对象
        OrderInfo orderInfo = getOrderInfo(orderId);
        //定义一个方法将orderInfo转换成map
        Map map = this.initWareOrder(orderInfo);
        //将map转换成json
        String wareJson = JSON.toJSONString(map);
        //发送消息
        rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_WARE_STOCK,MqConst.ROUTING_WARE_STOCK,wareJson);

    }
    //定义一个方法将orderInfo转换成map
    //方法后边拆单时候可以复用
    public Map initWareOrder(OrderInfo orderInfo) {
        HashMap<String, Object> map = new HashMap<>();

        map.put("orderId", orderInfo.getId());
        map.put("consignee", orderInfo.getConsignee());
        map.put("consigneeTel", orderInfo.getConsigneeTel());
        map.put("orderComment", orderInfo.getOrderComment());
        map.put("orderBody", orderInfo.getTradeBody());
        map.put("deliveryAddress", orderInfo.getDeliveryAddress());
        map.put("paymentWay", "2");
        map.put("wareId", orderInfo.getWareId());// 仓库Id ，减库存拆单时需要使用！

        List<OrderDetail> orderDetailList = orderInfo.getOrderDetailList();
        //details:[{skuId:101,skuNum:1,skuName:’小米手64G’}, {skuId:201,skuNum:1,skuName:’索尼耳机’}]
        //声明一个集合存储map
        List<Map> maps = new ArrayList<>();

        for (OrderDetail orderDetail : orderDetailList) {

            HashMap<String, Object> detailMap = new HashMap<>();
            detailMap.put("skuId", orderDetail.getSkuId());
            detailMap.put("skuNum", orderDetail.getSkuNum());
            detailMap.put("skuName", orderDetail.getSkuName());
            maps.add(detailMap);
        }
        //存储订单明细
        map.put("details",maps);

        return map;
    }

    /**
     * 根据orderId，wareSkuMap 获取子订单集合【拆单的业务逻辑】
     * @param orderId
     * @param wareSkuMap
     * @return
     */
    @Override
    public List<OrderInfo> orderSplit(String orderId, String wareSkuMap) {
        /**
         * 1.先获取原始订单
         * 2.将wareSkuMap变成能操作的对象
         * 3.给子订单赋值并添加子订单到集合中
         * 4.保存子订单
         * 5.更新原始订单的状态
         */
        List<OrderInfo> orderInfoList = new ArrayList<>();


        OrderInfo orderInfoOrigin = this.getOrderInfo(Long.parseLong(orderId));
        //将wareSkuMap变成能操作的对象
        //[{"wareId":"1","skuIds":["2","10"]},{"wareId":"2","skuIds":["3"]}]
        List<Map> mapList = JSON.parseArray(wareSkuMap, Map.class);
        if(!CollectionUtils.isEmpty(mapList)){
            for (Map map : mapList) {
                //获取仓库Id
                String wareId = (String) map.get("wareId");
                //获取仓库对应的商品Id
                List<String> skuIdList = (List<String>) map.get("skuIds");
                //创建一个子订单 -- 赋值
                OrderInfo subOrderInfo = new OrderInfo();
                BeanUtils.copyProperties(orderInfoOrigin,subOrderInfo);
                //设置主键为空
                subOrderInfo.setId(null);
                subOrderInfo.setParentOrderId(Long.parseLong(orderId));
                // 赋值仓库Id
                subOrderInfo.setWareId(wareId);

                //计算价格
                //根据skuId获取子订单明细
                //先获取原始订单明细
                List<OrderDetail> orderDetailList = orderInfoOrigin.getOrderDetailList();
                //声明一个子订单集合
                ArrayList<OrderDetail> orderDetails = new ArrayList<>();

                for (OrderDetail orderDetail : orderDetailList) {
                    //找到仓库对应的skuIdList
                    for (String skuId : skuIdList) {
                        //根据skuId判断
                        if(orderDetail.getSkuId()==Long.parseLong(skuId)){
                            //将子订单明细对象放入子订单对象
                            orderDetails.add(orderDetail);
                        }
                    }

                }

                subOrderInfo.setOrderDetailList(orderDetails);
                //计算价格
                subOrderInfo.sumTotalAmount();
                //保存子订单
                this.saveOrderInfo(subOrderInfo);
                //将子订单添加到集合
                orderInfoList.add(subOrderInfo);
            }
        }
        //修改原始订单状态
        this.updateOrderStatus(Long.parseLong(orderId),ProcessStatus.SPLIT);
        return orderInfoList;
    }

    /**
     * 取消订单业务【关闭orderInfo。paymentInfo】
     * @param orderId
     * @param flag
     */
    @Override
    public void execExpiredOrder(Long orderId, String flag) {

        //  进度中能够获取到订单状态
        //  后续会有很多地方，都会使用到根据进度状态来更新订单。
        updateOrderStatus(orderId,ProcessStatus.CLOSED);

        if("2".equals(flag)){
            //发送一个消息关闭电商本地的交易记录
            rabbitService.sendMessage(MqConst.EXCHANGE_DIRECT_PAYMENT_CLOSE, MqConst.ROUTING_PAYMENT_CLOSE, orderId);
        }
    }
}
