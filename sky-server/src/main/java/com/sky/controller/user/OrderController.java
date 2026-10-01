package com.sky.controller.user;


import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController("userOrderController")
@RequestMapping("/user/order")
@Slf4j
@Api(tags = "C端订单接口")
public class OrderController {

    @Autowired
    private OrderService orderService;

    /**
     *用户下单
     */
    @PostMapping("/submit")
    @ApiOperation("用户下单接口")
    public Result<OrderSubmitVO> submit(@RequestBody OrdersSubmitDTO ordersSubmitDTO){
        log.info("用户下单：{}",ordersSubmitDTO);
        OrderSubmitVO orderSubmitVO= orderService.submit(ordersSubmitDTO);
        return Result.success(orderSubmitVO);
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    @PutMapping("/payment")
    @ApiOperation("订单支付")
    public Result<OrderPaymentVO> payment(@RequestBody OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        log.info("订单支付：{}", ordersPaymentDTO);
        OrderPaymentVO orderPaymentVO = orderService.payment(ordersPaymentDTO);
        log.info("生成预支付交易单：{}", orderPaymentVO);
        return Result.success(orderPaymentVO);
    }

    /**
     * 历史订单分页查询
     */
    @GetMapping("/historyOrders")
    @ApiOperation("历史订单分页查询")
    public Result<PageResult> pageOrdersResult(@RequestParam int page, @RequestParam int pageSize,
                                             @RequestParam(required = false) Integer status){
        log.info("历史订单分页查询：{},{},{}",page,pageSize,status);
        PageResult pageResult = orderService.pageOrdersResult(page, pageSize, status);
        return Result.success(pageResult);
    }

    /**
     * 查询当前用户的订单详情
     * @param id 订单id
     */
    @GetMapping("/orderDetail/{id}")
    @ApiOperation("查询订单详情")
    public Result<OrderVO> details(@PathVariable Long id) {
        return Result.success(orderService.userDetails(id));
    }

    /**
     * 用户取消订单
     * @param id 订单id
     */
    @PutMapping("/cancel/{id}")
    @ApiOperation("取消订单")
    public Result<Void> cancel(@PathVariable Long id) throws Exception {
        orderService.userCancelById(id);
        return Result.success();
    }

    /**
     * 再来一单，将原订单商品加入购物车
     * @param id 原订单id
     */
    @PostMapping("/repetition/{id}")
    @ApiOperation("再来一单")
    public Result<Void> repetition(@PathVariable Long id) {
        orderService.repetition(id);
        return Result.success();
    }

    /**
     * 客户催单
     * @param id
     * @return
     */
    @GetMapping("/reminder/{id}")
    @ApiOperation("客户催单接口")
    public Result reminder(@PathVariable Long id){
        orderService.reminder(id);
        return Result.success();
    }
}
