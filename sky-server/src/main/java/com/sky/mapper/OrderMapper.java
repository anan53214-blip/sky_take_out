package com.sky.mapper;

import com.github.pagehelper.Page;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.Orders;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface OrderMapper {
    /**
     * 插入订单数据
     * @param orders
     */
    void insert(Orders orders);

    /**
     * 根据订单号查询订单
     * @param orderNumber
     */
    @Select("select * from orders where number = #{orderNumber}")
    Orders getByNumber(String orderNumber);

    /**
     * 修改订单信息
     * @param orders
     */
    void update(Orders orders);

    /**
     * 历史订单分页查询
     */
    Page<Orders> pageQuery(OrdersPageQueryDTO ordersPageQueryDTO);

    /**
     * 根据订单id查询订单
     * @param id 订单id
     */
    @Select("select * from orders where id = #{id}")
    Orders getById(Long id);

    /**
     * 根据订单id查询并加行锁，配合事务防止并发修改订单状态
     * @param id 订单id
     */
    @Select("select * from orders where id = #{id} for update")
    Orders getByIdForUpdate(Long id);

    /**
     * 根据订单号查询并加行锁，用于支付及支付成功处理
     * @param number 订单号
     */
    @Select("select * from orders where number = #{number} for update")
    Orders getByNumberForUpdate(String number);

    /**
     * 根据订单状态统计数量
     * @param status 订单状态
     */
    @Select("select count(*) from orders where status = #{status}")
    Integer countStatus(Integer status);

    /**
     * 根据订单状态和下单时间查询
     * @param status
     * @param ordertime
     * @return
     */
    @Select("select * from orders where status = #{status} and order_time < #{ordertime}")
    List<Orders> getByStatusAndOrderTimeLT(Integer status, LocalDateTime ordertime);

    /**
     * 根据条件查询营业额
     * @param map
     * @return
     */
    Double sumByMap(Map map);

    /**
     * 根据条件查询订单数据
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
