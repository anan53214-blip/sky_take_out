package com.sky.service;

/**
 * 配送范围校验服务。
 */
public interface DeliveryRangeService {

    /**
     * 校验收货地址是否在店铺配送范围内。
     *
     * @param address 完整收货地址
     */
    void check(String address);
}
