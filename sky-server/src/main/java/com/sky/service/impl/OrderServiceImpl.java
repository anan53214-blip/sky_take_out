package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.dto.OrdersConfirmDTO;
import com.sky.dto.OrdersRejectionDTO;
import com.sky.dto.OrdersCancelDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import com.sky.vo.OrderStatisticsVO;
import com.sky.service.DeliveryRangeService;
import com.sky.websocket.WebSocketServer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;
    @Autowired
    private DeliveryRangeService deliveryRangeService;
    @Autowired
    private WebSocketServer webSocketServer;

    @Value("${sky.payment.mock-enabled:false}")
    private boolean mockPaymentEnabled;

    /**
     *用户下单
     */
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常（地址簿为空，购物车数据为空）
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        Long userId = currentUserId();
        if(addressBook == null || !userId.equals(addressBook.getUserId())){
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        // 拼接完整收货地址；默认配送校验直接放行，启用真实地图校验后再判断距离。
        String address = addressPart(addressBook.getProvinceName())
                + addressPart(addressBook.getCityName()) + addressPart(addressBook.getDistrictName())
                + addressPart(addressBook.getDetail());
        deliveryRangeService.check(address);
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);
        List<ShoppingCart> list = shoppingCartMapper.list(shoppingCart);
        if(list == null || list.size()==0){
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }
        //向订单表插入1条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBook.getPhone());
        // 保存下单时的地址快照，后续修改地址簿不会改变历史订单的收货地址。
        orders.setAddress(address);
        orders.setUserId(userId);
        orders.setConsignee(addressBook.getConsignee());
        orderMapper.insert(orders);
        //向订单明细表插入数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : list) {
            OrderDetail orderDetail = new OrderDetail();
            // 订单明细使用自己的主键，不能复制购物车记录的id。
            BeanUtils.copyProperties(cart, orderDetail, "id");
            orderDetail.setOrderId(orders.getId());
            orderDetailList.add(orderDetail);
        }

        orderDetailMapper.insertBatch(orderDetailList);
        //清空当前购物车数据
        shoppingCartMapper.deleteAll(userId);
        //封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderTime(orders.getOrderTime())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .build();

        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 本地学习使用：模拟支付成功，沿用原有的支付成功业务处理。
        if (mockPaymentEnabled) {
            if (ordersPaymentDTO.getOrderNumber() == null || ordersPaymentDTO.getOrderNumber().trim().isEmpty()) {
                throw new OrderBusinessException("订单号不能为空");
            }
            Orders orders = orderMapper.getByNumberForUpdate(ordersPaymentDTO.getOrderNumber());
            if (orders == null) {
                throw new OrderBusinessException("订单不存在");
            }
            Long currentUserId = BaseContext.getCurrentId();
            if (currentUserId == null || !currentUserId.equals(orders.getUserId())) {
                throw new OrderBusinessException("不能支付其他用户的订单");
            }
            if (!Integer.valueOf(1).equals(ordersPaymentDTO.getPayMethod())) {
                throw new OrderBusinessException("模拟支付仅支持微信支付");
            }
            if (Orders.CANCELLED.equals(orders.getStatus()) || Orders.REFUND.equals(orders.getPayStatus())) {
                throw new OrderBusinessException("订单已取消或退款，不能支付");
            }
            // 重复点击不再更新订单，避免把已接单、已完成的订单改回待接单。
            if (!Orders.PAID.equals(orders.getPayStatus())) {
                if (!Orders.PENDING_PAYMENT.equals(orders.getStatus()) || !Orders.UN_PAID.equals(orders.getPayStatus())) {
                    throw new OrderBusinessException("当前订单状态不允许支付");
                }
                paySuccess(orders.getNumber());
            }
            return OrderPaymentVO.builder().mockPayment(true).build();
        }

        // 当前登录用户id
        // 真实支付同样校验订单归属和状态，避免支付其他用户或已失效的订单。
        Orders paymentOrder = orderMapper.getByNumberForUpdate(ordersPaymentDTO.getOrderNumber());
        requireOwned(paymentOrder);
        requireStatus(paymentOrder, Orders.PENDING_PAYMENT);
        if (!Orders.UN_PAID.equals(paymentOrder.getPayStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        Long userId = currentUserId();
        User user = userMapper.getById(userId);

        //调用微信支付接口，生成预支付交易单
        JSONObject jsonObject = weChatPayUtil.pay(
                ordersPaymentDTO.getOrderNumber(), //商户订单号
                new BigDecimal(0.01), //支付金额，单位 元
                "苍穹外卖订单", //商品描述
                user.getOpenid() //微信用户的openid
        );

        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
            throw new OrderBusinessException("该订单已支付");
        }

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        return vo;
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String outTradeNo) {

        // 根据订单号查询订单
        Orders ordersDB = orderMapper.getByNumberForUpdate(outTradeNo);
        requireExists(ordersDB);
        // 支付通知可能重复到达，已支付或已退款的订单不再更新，避免状态回退。
        if (Orders.PAID.equals(ordersDB.getPayStatus()) || Orders.REFUND.equals(ordersDB.getPayStatus())) {
            return;
        }
        requireStatus(ordersDB, Orders.PENDING_PAYMENT);

        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
        Orders orders = Orders.builder()
                .id(ordersDB.getId())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .payMethod(1)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        // 订单支付成功后，向商家端发送WebSocket消息，通知有新订单 type orderId content
        Map map = new HashMap();
        map.put("type", 1);//1表示来单提醒，2表示客户催单
        map.put("orderId", ordersDB.getId());
        map.put("content", "订单号"+outTradeNo);
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 历史订单分页查询
     */
    public PageResult pageOrdersResult(int page,int pageSize,Integer status) {
        validatePage(page, pageSize);
        // 用户id从登录上下文获取，只查询当前用户的订单。
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(currentUserId());
        ordersPageQueryDTO.setStatus(status);
        // 先对订单表分页，再补充本页订单明细，避免一对多联表导致重复订单和总数错误。
        PageHelper.startPage(page,pageSize);
        Page<Orders> pages= orderMapper.pageQuery(ordersPageQueryDTO);
        List<OrderVO> records = pages.getResult().stream().map(this::toOrderVO).collect(Collectors.toList());
        return new PageResult(pages.getTotal(), records);
    }

    /**
     * 商家端订单分页搜索
     * @param dto 分页及查询条件
     * @return 订单总数和当前页订单数据
     */
    public PageResult conditionSearch(OrdersPageQueryDTO dto) {
        validatePage(dto.getPage(), dto.getPageSize());
        if (dto.getBeginTime() != null && dto.getEndTime() != null
                && dto.getBeginTime().isAfter(dto.getEndTime())) {
            throw new OrderBusinessException("开始时间不能晚于结束时间");
        }
        PageHelper.startPage(dto.getPage(), dto.getPageSize());
        Page<Orders> page = orderMapper.pageQuery(dto);
        List<OrderVO> records = page.getResult().stream().map(this::toOrderVO).collect(Collectors.toList());
        return new PageResult(page.getTotal(), records);
    }

    /**
     * 商家查询订单详情
     * @param id 订单id
     */
    public OrderVO details(Long id) {
        Orders order = orderMapper.getById(id);
        requireExists(order);
        return toOrderVO(order);
    }

    /**
     * 用户查询自己的订单详情，查询前校验订单归属
     * @param id 订单id
     */
    public OrderVO userDetails(Long id) {
        Orders order = orderMapper.getById(id);
        requireOwned(order);
        return toOrderVO(order);
    }

    /**
     * 将订单及商品明细封装为页面需要的响应数据
     * @param order 订单基本信息
     * @return 包含商品明细和菜品摘要的订单信息
     */
    private OrderVO toOrderVO(Orders order) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(order, vo);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(order.getId());
        vo.setOrderDetailList(details);
        // 商家订单列表使用菜品摘要，格式示例：宫保鸡丁*3;鱼香肉丝*1;
        vo.setOrderDishes(details.stream().map(d -> d.getName() + "*" + d.getNumber() + ";")
                .collect(Collectors.joining()));
        return vo;
    }

    /**
     * 统计待接单、待派送和派送中的订单数量
     */
    public OrderStatisticsVO statistics() {
        OrderStatisticsVO vo = new OrderStatisticsVO();
        vo.setToBeConfirmed(orderMapper.countStatus(Orders.TO_BE_CONFIRMED));
        vo.setConfirmed(orderMapper.countStatus(Orders.CONFIRMED));
        vo.setDeliveryInProgress(orderMapper.countStatus(Orders.DELIVERY_IN_PROGRESS));
        return vo;
    }

    /**
     * 用户取消订单，仅允许取消待付款和待接单订单
     * @param id 订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void userCancelById(Long id) throws Exception {
        // 在事务中锁定订单，避免取消与商家接单或支付处理同时覆盖状态。
        Orders order = orderMapper.getByIdForUpdate(id);
        requireOwned(order);
        if (!Orders.PENDING_PAYMENT.equals(order.getStatus()) && !Orders.TO_BE_CONFIRMED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        cancelOrder(order, "用户取消", null);
    }

    /**
     * 再来一单，将原订单商品重新加入当前用户购物车
     * @param id 原订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void repetition(Long id) {
        Orders order = orderMapper.getById(id);
        requireOwned(order);
        List<OrderDetail> details = orderDetailMapper.getByOrderId(id);
        List<ShoppingCart> carts = new ArrayList<>();
        for (OrderDetail detail : details) {
            ShoppingCart cart = new ShoppingCart();
            // 保留商品、口味和数量，购物车记录的主键由数据库重新生成。
            BeanUtils.copyProperties(detail, cart, "id");
            cart.setUserId(currentUserId());
            cart.setCreateTime(LocalDateTime.now());
            carts.add(cart);
        }
        // 没有明细时不执行批量插入，避免生成空的values语句。
        if (!carts.isEmpty()) {
            shoppingCartMapper.insertBatch(carts);
        }
    }

    /**
     * 商家接单，将待接单状态修改为已接单
     * @param dto 接单订单信息
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirm(OrdersConfirmDTO dto) {
        Orders order = orderMapper.getByIdForUpdate(dto.getId());
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        orderMapper.update(Orders.builder().id(order.getId()).status(Orders.CONFIRMED).build());
    }

    /**
     * 商家拒绝待接单订单，记录拒单原因并处理退款
     * @param dto 订单id及拒单原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void rejection(OrdersRejectionDTO dto) throws Exception {
        requireReason(dto.getRejectionReason());
        Orders order = orderMapper.getByIdForUpdate(dto.getId());
        requireStatus(order, Orders.TO_BE_CONFIRMED);
        cancelOrder(order, null, dto.getRejectionReason());
    }

    /**
     * 商家取消进行中的订单，已完成和已取消订单不允许再次取消
     * @param dto 订单id及取消原因
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancel(OrdersCancelDTO dto) throws Exception {
        requireReason(dto.getCancelReason());
        Orders order = orderMapper.getByIdForUpdate(dto.getId());
        requireExists(order);
        if (Orders.COMPLETED.equals(order.getStatus()) || Orders.CANCELLED.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
        cancelOrder(order, dto.getCancelReason(), null);
    }

    /**
     * 用户取消、商家取消和拒单共用的取消及退款处理
     * @param order 已查询并加锁的订单
     * @param cancelReason 取消原因，拒单时为空
     * @param rejectionReason 拒单原因，普通取消时为空
     */
    private void cancelOrder(Orders order, String cancelReason, String rejectionReason) throws Exception {
        Orders update = Orders.builder().id(order.getId()).status(Orders.CANCELLED)
                .cancelReason(cancelReason).rejectionReason(rejectionReason)
                .cancelTime(LocalDateTime.now()).build();
        // 未支付订单无需退款；模拟支付跳过微信接口，直接更新本地退款状态。
        if (Orders.PAID.equals(order.getPayStatus())) {
            if (!mockPaymentEnabled) {
                // 与现有学习版微信支付的 0.01 元实付金额保持一致。
                String response = weChatPayUtil.refund(order.getNumber(), order.getNumber(),
                        new BigDecimal("0.01"), new BigDecimal("0.01"));
                JSONObject refund;
                try {
                    refund = JSONObject.parseObject(response);
                } catch (Exception ex) {
                    throw new OrderBusinessException("退款申请失败，请稍后重试");
                }
                // 只有退款成功或已受理时才继续取消；失败则抛出异常，不更新订单。
                if (refund == null || refund.getString("code") != null
                        || !("SUCCESS".equals(refund.getString("status"))
                        || "PROCESSING".equals(refund.getString("status")))) {
                    throw new OrderBusinessException("退款申请失败，请稍后重试");
                }
            }
            // 沿用课程支付状态2表示退款；真实退款已受理不等于资金已经到账。
            update.setPayStatus(Orders.REFUND);
        }
        orderMapper.update(update);
    }

    /**
     * 派送订单，仅允许从已接单状态进入派送中
     * @param id 订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void delivery(Long id) {
        Orders order = orderMapper.getByIdForUpdate(id);
        requireStatus(order, Orders.CONFIRMED);
        orderMapper.update(Orders.builder().id(id).status(Orders.DELIVERY_IN_PROGRESS).build());
    }

    /**
     * 完成订单，仅允许完成派送中的订单，并记录实际送达时间
     * @param id 订单id
     */
    @Transactional(rollbackFor = Exception.class)
    public void complete(Long id) {
        Orders order = orderMapper.getByIdForUpdate(id);
        requireStatus(order, Orders.DELIVERY_IN_PROGRESS);
        orderMapper.update(Orders.builder().id(id).status(Orders.COMPLETED).deliveryTime(LocalDateTime.now()).build());
    }

    /**
     * 客户催单
     * @param id
     * @return
     */
    public void reminder(Long id) {
        Orders order = orderMapper.getByIdForUpdate(id);
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        Map map = new HashMap();
        map.put("type", 2);//1表示来单提醒，2表示客户催单
        map.put("orderId", id);
        map.put("content", "订单号"+order.getNumber());
        String json = JSON.toJSONString(map);
        webSocketServer.sendToAllClient(json);
    }

    /**
     * 校验订单是否存在
     */
    private void requireExists(Orders order) {
        if (order == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
    }

    /**
     * 校验订单归属，防止用户查询或操作其他用户的订单
     */
    private void requireOwned(Orders order) {
        requireExists(order);
        if (!currentUserId().equals(order.getUserId())) {
            throw new OrderBusinessException("不能操作其他用户的订单");
        }
    }

    /**
     * 校验订单存在且处于指定状态，防止跳过业务流程修改状态
     */
    private void requireStatus(Orders order, Integer status) {
        requireExists(order);
        if (!status.equals(order.getStatus())) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }
    }

    /**
     * 获取当前登录用户id，未登录时抛出业务异常
     */
    private Long currentUserId() {
        Long id = BaseContext.getCurrentId();
        if (id == null) {
            throw new OrderBusinessException(MessageConstant.USER_NOT_LOGIN);
        }
        return id;
    }

    /**
     * 校验商家取消或拒单原因不能为空
     */
    private void requireReason(String reason) {
        if (reason == null || reason.trim().isEmpty()) {
            throw new OrderBusinessException("取消或拒单原因不能为空");
        }
    }

    /**
     * 校验页码和每页记录数，避免无效分页参数
     */
    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1) {
            throw new OrderBusinessException("页码和每页记录数必须大于0");
        }
    }

    /**
     * 将空地址片段转为空字符串，避免拼接出字面值null
     */
    private String addressPart(String part) {
        return part == null ? "" : part;
    }

}
