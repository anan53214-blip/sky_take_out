package com.sky.mapper;

import com.sky.entity.OrderDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface OrderDetailMapper {
    /**
     * 批量插入订单商品明细
     * @param orderDetailList 订单明细集合
     */
    void insertBatch(List<OrderDetail> orderDetailList);

    /**
     * 根据订单id查询商品明细
     * @param orderId 订单id
     */
    @Select("select * from order_detail where order_id = #{orderId} order by id")
    List<OrderDetail> getByOrderId(Long orderId);
}
