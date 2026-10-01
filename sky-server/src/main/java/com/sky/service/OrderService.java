package com.sky.service;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.result.PageResult;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderStatisticsVO;

public interface OrderService {

    /**
     *用户下单
     */
    OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO);

    /**
     * 订单支付
     * @param ordersPaymentDTO
     * @return
     */
    OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception;

    /**
     * 支付成功，修改订单状态
     * @param outTradeNo
     */
    void paySuccess(String outTradeNo);

    /**
     * 历史订单分页查询
     */
    PageResult pageOrdersResult(int page,int pageSize,Integer status);

    /**
     * 用户查询自己的订单详情
     * @param id 订单id
     * @return 订单基本信息及商品明细
     */
    OrderVO userDetails(Long id);

    /**
     * 商家查询订单详情
     * @param id 订单id
     * @return 订单基本信息及商品明细
     */
    OrderVO details(Long id);

    /**
     * 用户取消待付款或待接单的订单，已支付时申请退款
     * @param id 订单id
     */
    void userCancelById(Long id) throws Exception;

    /**
     * 再来一单，将原订单商品重新加入当前用户购物车
     * @param id 原订单id
     */
    void repetition(Long id);

    /**
     * 商家根据订单号、手机号、状态和下单时间分页搜索订单
     * @param dto 分页及查询条件
     * @return 订单总数和当前页订单数据
     */
    PageResult conditionSearch(OrdersPageQueryDTO dto);

    /**
     * 统计待接单、待派送和派送中的订单数量
     * @return 各状态订单数量
     */
    OrderStatisticsVO statistics();

    /**
     * 商家接单，将待接单订单修改为已接单
     * @param dto 接单订单信息
     */
    void confirm(OrdersConfirmDTO dto);

    /**
     * 商家拒绝待接单订单，记录拒单原因并处理退款
     * @param dto 订单id及拒单原因
     */
    void rejection(OrdersRejectionDTO dto) throws Exception;

    /**
     * 商家取消进行中的订单，记录取消原因并处理退款
     * @param dto 订单id及取消原因
     */
    void cancel(OrdersCancelDTO dto) throws Exception;

    /**
     * 派送已接单的订单
     * @param id 订单id
     */
    void delivery(Long id);

    /**
     * 完成派送中的订单并记录送达时间
     * @param id 订单id
     */
    void complete(Long id);

    /**
     * 客户催单
     * @param id
     * @return
     */
    void reminder(Long id);
}
