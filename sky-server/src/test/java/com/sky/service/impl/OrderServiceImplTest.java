package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.context.BaseContext;
import com.sky.dto.*;
import com.sky.entity.*;
import com.sky.exception.OrderBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderVO;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 订单业务测试，使用模拟Mapper验证分页、用户归属、退款和状态流转。 */
class OrderServiceImplTest {
    @Mock private OrderMapper orderMapper;
    @Mock private OrderDetailMapper orderDetailMapper;
    @Mock private ShoppingCartMapper shoppingCartMapper;
    @Mock private WeChatPayUtil weChatPayUtil;
    @InjectMocks private OrderServiceImpl service;
    private AutoCloseable mocks;

    @BeforeEach
    void setup() {
        mocks = MockitoAnnotations.openMocks(this);
        BaseContext.setCurrentId(7L);
    }

    @AfterEach
    void cleanup() throws Exception {
        PageHelper.clearPage();
        BaseContext.removeCurrentId();
        mocks.close();
    }

    private Orders order(Integer status, Integer payStatus) {
        return Orders.builder().id(1L).userId(7L).number("order-1")
                .status(status).payStatus(payStatus).build();
    }

    /** 验证历史订单只查询登录用户，并保留订单总数和完整商品明细。 */
    @Test
    void historyUsesLoggedInUserAndPreservesTotalAndDetails() {
        Page<Orders> page = new Page<>(2, 10);
        page.setTotal(21);
        page.add(order(Orders.COMPLETED, Orders.PAID));
        List<OrderDetail> details = Collections.singletonList(
                OrderDetail.builder().name("宫保鸡丁").number(3).build());
        when(orderMapper.pageQuery(any())).thenReturn(page);
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(details);
        PageResult result = service.pageOrdersResult(2, 10, Orders.COMPLETED);
        ArgumentCaptor<OrdersPageQueryDTO> dto = ArgumentCaptor.forClass(OrdersPageQueryDTO.class);
        verify(orderMapper).pageQuery(dto.capture());
        assertEquals(7L, dto.getValue().getUserId());
        assertEquals(Orders.COMPLETED, dto.getValue().getStatus());
        assertEquals(21, result.getTotal());
        assertEquals(1, result.getRecords().size());
        OrderVO vo = (OrderVO) result.getRecords().get(0);
        assertEquals(details, vo.getOrderDetailList());
        assertEquals("宫保鸡丁*3;", vo.getOrderDishes());
    }

    /** 验证没有历史订单时返回空集合，不额外查询商品明细。 */
    @Test
    void emptyHistoryReturnsEmptyRecords() {
        Page<Orders> page = new Page<>(1, 10);
        page.setTotal(0);
        when(orderMapper.pageQuery(any())).thenReturn(page);
        PageResult result = service.pageOrdersResult(1, 10, null);
        assertEquals(0, result.getTotal());
        assertTrue(result.getRecords().isEmpty());
        verifyNoInteractions(orderDetailMapper);
    }

    /** 验证未登录或分页参数无效时不会查询数据库。 */
    @Test
    void historyRejectsMissingUserAndInvalidPageBeforeQuerying() {
        assertThrows(OrderBusinessException.class, () -> service.pageOrdersResult(0, 10, null));
        BaseContext.removeCurrentId();
        assertThrows(OrderBusinessException.class, () -> service.pageOrdersResult(1, 10, null));
        verifyNoInteractions(orderMapper);
    }

    /** 验证订单不存在时返回业务异常。 */
    @Test
    void detailsRejectsMissingOrder() {
        assertThrows(OrderBusinessException.class, () -> service.details(123L));
        verifyNoInteractions(orderDetailMapper);
    }

    /** 验证用户不能查看或再次购买其他用户的订单。 */
    @Test
    void userCannotReadOrRepeatSomeoneElsesOrder() {
        Orders other = order(Orders.COMPLETED, Orders.PAID);
        other.setUserId(8L);
        when(orderMapper.getById(1L)).thenReturn(other);
        assertThrows(OrderBusinessException.class, () -> service.userDetails(1L));
        assertThrows(OrderBusinessException.class, () -> service.repetition(1L));
        verifyNoInteractions(orderDetailMapper, shoppingCartMapper);
    }

    /** 验证用户不能取消其他用户的订单。 */
    @Test
    void userCannotCancelSomeoneElsesOrder() {
        Orders other = order(Orders.PENDING_PAYMENT, Orders.UN_PAID);
        other.setUserId(8L);
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(other);
        assertThrows(OrderBusinessException.class, () -> service.userCancelById(1L));
        verify(orderMapper, never()).update(any());
    }

    /** 验证未付款订单取消时记录原因和时间，不调用退款接口。 */
    @Test
    void unpaidCancellationDoesNotRefund() throws Exception {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.PENDING_PAYMENT, Orders.UN_PAID));
        service.userCancelById(1L);
        ArgumentCaptor<Orders> update = ArgumentCaptor.forClass(Orders.class);
        verify(orderMapper).update(update.capture());
        assertEquals(Orders.CANCELLED, update.getValue().getStatus());
        assertEquals("用户取消", update.getValue().getCancelReason());
        assertNotNull(update.getValue().getCancelTime());
        assertNull(update.getValue().getPayStatus());
        verifyNoInteractions(weChatPayUtil);
    }

    /** 验证模拟支付取消后标记退款，不调用微信。 */
    @Test
    void mockCancellationMarksRefundWithoutCallingWechat() throws Exception {
        ReflectionTestUtils.setField(service, "mockPaymentEnabled", true);
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.TO_BE_CONFIRMED, Orders.PAID));
        service.userCancelById(1L);
        verify(orderMapper).update(argThat(o -> Orders.REFUND.equals(o.getPayStatus())));
        verifyNoInteractions(weChatPayUtil);
    }

    /** 验证用户不能直接取消商家已接单的订单。 */
    @Test
    void userCannotCancelAcceptedOrder() {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.CONFIRMED, Orders.PAID));
        assertThrows(OrderBusinessException.class, () -> service.userCancelById(1L));
        verify(orderMapper, never()).update(any());
    }

    /** 验证商家拒单时申请退款并记录拒单原因。 */
    @Test
    void rejectionRefundsPaidOrderAndRecordsReason() throws Exception {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.TO_BE_CONFIRMED, Orders.PAID));
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"status\":\"PROCESSING\"}");
        OrdersRejectionDTO dto = new OrdersRejectionDTO();
        dto.setId(1L);
        dto.setRejectionReason("售罄");
        service.rejection(dto);
        verify(weChatPayUtil).refund(eq("order-1"), eq("order-1"), any(), any());
        verify(orderMapper).update(argThat(o -> Orders.CANCELLED.equals(o.getStatus())
                && Orders.REFUND.equals(o.getPayStatus()) && "售罄".equals(o.getRejectionReason())));
    }

    /** 验证退款调用抛出异常时不更新订单状态。 */
    @Test
    void failedRefundDoesNotCancelOrder() throws Exception {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.CONFIRMED, Orders.PAID));
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any())).thenThrow(new Exception("refund failed"));
        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(1L);
        dto.setCancelReason("无法配送");
        assertThrows(Exception.class, () -> service.cancel(dto));
        verify(orderMapper, never()).update(any());
    }

    /** 验证微信返回退款失败时不取消订单。 */
    @Test
    void rejectedRefundResponseDoesNotCancelOrder() throws Exception {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.TO_BE_CONFIRMED, Orders.PAID));
        when(weChatPayUtil.refund(anyString(), anyString(), any(), any()))
                .thenReturn("{\"code\":\"NOT_ENOUGH\",\"message\":\"余额不足\"}");
        assertThrows(OrderBusinessException.class, () -> service.userCancelById(1L));
        verify(orderMapper, never()).update(any());
    }

    /** 验证商家取消必须填写原因，且不能取消已完成订单。 */
    @Test
    void cancellationRequiresReasonAndRejectsCompletedOrder() {
        OrdersCancelDTO dto = new OrdersCancelDTO();
        dto.setId(1L);
        assertThrows(OrderBusinessException.class, () -> service.cancel(dto));
        dto.setCancelReason("无法配送");
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.COMPLETED, Orders.PAID));
        assertThrows(OrderBusinessException.class, () -> service.cancel(dto));
        verify(orderMapper, never()).update(any());
    }

    /** 验证再来一单保留商品口味和数量，并使用新的购物车主键。 */
    @Test
    void repeatCopiesItemsWithNewIdentityAndCurrentUser() {
        when(orderMapper.getById(1L)).thenReturn(order(Orders.COMPLETED, Orders.PAID));
        when(orderDetailMapper.getByOrderId(1L)).thenReturn(Collections.singletonList(
                OrderDetail.builder().id(99L).orderId(1L).dishId(5L).dishFlavor("辣").number(3).build()));
        service.repetition(1L);
        ArgumentCaptor<List<ShoppingCart>> carts = ArgumentCaptor.forClass(List.class);
        verify(shoppingCartMapper).insertBatch(carts.capture());
        ShoppingCart cart = carts.getValue().get(0);
        assertNull(cart.getId());
        assertEquals(7L, cart.getUserId());
        assertEquals(3, cart.getNumber());
        assertEquals("辣", cart.getDishFlavor());
        assertNotNull(cart.getCreateTime());
    }

    /** 验证订单按接单、派送、完成的顺序流转，并记录送达时间。 */
    @Test
    void orderProgressesThroughConfirmDeliveryAndComplete() {
        Orders order = order(Orders.TO_BE_CONFIRMED, Orders.PAID);
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order);
        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(1L);
        service.confirm(dto);
        verify(orderMapper).update(argThat(o -> Orders.CONFIRMED.equals(o.getStatus())));
        order.setStatus(Orders.CONFIRMED);
        service.delivery(1L);
        verify(orderMapper).update(argThat(o -> Orders.DELIVERY_IN_PROGRESS.equals(o.getStatus())));
        order.setStatus(Orders.DELIVERY_IN_PROGRESS);
        service.complete(1L);
        verify(orderMapper).update(argThat(o -> Orders.COMPLETED.equals(o.getStatus()) && o.getDeliveryTime() != null));
    }

    /** 验证跳过业务状态的操作不会更新订单。 */
    @Test
    void illegalTransitionsDoNotUpdateOrder() {
        when(orderMapper.getByIdForUpdate(1L)).thenReturn(order(Orders.PENDING_PAYMENT, Orders.UN_PAID));
        OrdersConfirmDTO dto = new OrdersConfirmDTO();
        dto.setId(1L);
        assertThrows(OrderBusinessException.class, () -> service.confirm(dto));
        assertThrows(OrderBusinessException.class, () -> service.delivery(1L));
        assertThrows(OrderBusinessException.class, () -> service.complete(1L));
        verify(orderMapper, never()).update(any());
    }

    /** 验证重复支付通知不会将已完成订单改回待接单。 */
    @Test
    void duplicatePaymentCallbackDoesNotRegressOrder() {
        when(orderMapper.getByNumberForUpdate("order-1")).thenReturn(order(Orders.COMPLETED, Orders.PAID));
        service.paySuccess("order-1");
        verify(orderMapper, never()).update(any());
    }

    /** 验证开始时间晚于结束时间时拒绝查询。 */
    @Test
    void searchRejectsReversedTimeRange() {
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setPage(1);
        dto.setPageSize(10);
        dto.setBeginTime(LocalDateTime.of(2026, 10, 2, 0, 0));
        dto.setEndTime(LocalDateTime.of(2026, 10, 1, 0, 0));
        assertThrows(OrderBusinessException.class, () -> service.conditionSearch(dto));
        verifyNoInteractions(orderMapper);
    }
}
