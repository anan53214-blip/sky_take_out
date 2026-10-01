package com.sky.controller.admin;

import com.sky.dto.*;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderStatisticsVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 商家端订单管理接口
 */
@RestController("adminOrderController")
@RequestMapping("/admin/order")
@Api(tags = "订单管理接口")
public class OrderController {
    @Autowired
    private OrderService orderService;

    /**
     * 订单分页搜索
     * @param dto 分页及查询条件
     * @return 分页结果
     */
    @GetMapping("/conditionSearch")
    @ApiOperation("订单搜索")
    public Result<PageResult> conditionSearch(OrdersPageQueryDTO dto) {
        return Result.success(orderService.conditionSearch(dto));
    }

    /**
     * 统计待接单、待派送和派送中的订单数量
     */
    @GetMapping("/statistics")
    @ApiOperation("各个状态的订单数量统计")
    public Result<OrderStatisticsVO> statistics() {
        return Result.success(orderService.statistics());
    }

    /**
     * 查询订单详情
     * @param id 订单id
     */
    @GetMapping("/details/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.details(id));
    }

    /**
     * 商家接单
     * @param dto 接单订单信息
     */
    @PutMapping("/confirm")
    @ApiOperation("接单")
    public Result<Void> confirm(@RequestBody OrdersConfirmDTO dto) {
        orderService.confirm(dto);
        return Result.success();
    }

    /**
     * 商家拒单
     * @param dto 订单id及拒单原因
     */
    @PutMapping("/rejection")
    @ApiOperation("拒单")
    public Result<Void> rejection(@RequestBody OrdersRejectionDTO dto) throws Exception {
        orderService.rejection(dto);
        return Result.success();
    }

    /**
     * 商家取消订单
     * @param dto 订单id及取消原因
     */
    @PutMapping("/cancel")
    @ApiOperation("取消订单")
    public Result<Void> cancel(@RequestBody OrdersCancelDTO dto) throws Exception {
        orderService.cancel(dto);
        return Result.success();
    }

    /**
     * 派送订单
     * @param id 订单id
     */
    @PutMapping("/delivery/{id}")
    @ApiOperation("派送订单")
    public Result<Void> delivery(@PathVariable Long id) {
        orderService.delivery(id);
        return Result.success();
    }

    /**
     * 完成订单
     * @param id 订单id
     */
    @PutMapping("/complete/{id}")
    @ApiOperation("完成订单")
    public Result<Void> complete(@PathVariable Long id) {
        orderService.complete(id);
        return Result.success();
    }
}
