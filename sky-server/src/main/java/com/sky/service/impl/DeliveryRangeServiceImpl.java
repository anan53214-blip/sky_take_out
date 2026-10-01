package com.sky.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.sky.exception.OrderBusinessException;
import com.sky.service.DeliveryRangeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * 配送范围校验实现类
 * 默认所有地址可配送；启用真实校验后，根据百度地图驾车路线距离判断配送范围。
 */
@Service
public class DeliveryRangeServiceImpl implements DeliveryRangeService {
    // 是否启用真实地图校验，默认关闭，使用本地模拟放行模式。
    @Value("${sky.delivery.range-check-enabled:false}")
    private boolean enabled;
    // 最大配送距离，单位为米。
    @Value("${sky.delivery.max-distance:5000}")
    private int maxDistance;
    // 店铺完整地址，仅在启用真实地图校验时需要填写。
    @Value("${sky.shop.address:}")
    private String shopAddress;
    // 百度地图服务端应用AK，本地模拟模式无需配置。
    @Value("${sky.baidu.ak:}")
    private String ak;

    private final RestTemplate restTemplate;

    /**
     * 初始化地图请求客户端，设置连接和读取超时，避免下单长时间等待。
     */
    public DeliveryRangeServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        restTemplate = new RestTemplate(factory);
    }

    /**
     * 校验收货地址是否在配送范围内
     * @param address 完整收货地址
     */
    @Override
    public void check(String address) {
        if (!enabled) {
            // 本地学习模式：模拟地址在配送范围内，无需店铺地址、AK 或地图网络请求。
            return;
        }
        if (!StringUtils.hasText(shopAddress) || !StringUtils.hasText(ak) || maxDistance <= 0) {
            throw new OrderBusinessException("配送范围配置不完整");
        }
        // 先通过地理编码将店铺地址和收货地址转换为经纬度。
        String origin = coordinate(shopAddress, "店铺地址解析失败");
        String destination = coordinate(address, "收货地址解析失败");
        // 使用轻量级驾车路线规划接口获取实际路线距离，不使用两点直线距离。
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/directionlite/v1/driving")
                .queryParam("ak", ak).queryParam("origin", origin).queryParam("destination", destination)
                .queryParam("steps_info", "0").build().encode().toUri();
        JSONObject response = request(uri, "配送路线规划失败");
        JSONObject result = response.getJSONObject("result");
        JSONArray routes = result == null ? null : result.getJSONArray("routes");
        if (routes == null || routes.isEmpty() || routes.getJSONObject(0) == null) {
            throw new OrderBusinessException("配送路线规划失败");
        }
        // 取第一条路线的距离，恰好等于最大配送距离时仍允许配送。
        Integer distance = routes.getJSONObject(0).getInteger("distance");
        if (distance == null || distance < 0) {
            throw new OrderBusinessException("配送路线规划失败");
        }
        if (distance > maxDistance) {
            throw new OrderBusinessException("超出配送范围");
        }
    }

    /**
     * 调用百度地图地理编码接口解析地址
     * @param address 完整地址
     * @param error 地址解析失败时的业务提示
     * @return 路线规划要求的坐标格式：纬度,经度
     */
    private String coordinate(String address, String error) {
        if (!StringUtils.hasText(address)) {
            throw new OrderBusinessException(error);
        }
        URI uri = UriComponentsBuilder.fromHttpUrl("https://api.map.baidu.com/geocoding/v3/")
                .queryParam("address", address).queryParam("output", "json").queryParam("ak", ak)
                .build().encode().toUri();
        JSONObject response = request(uri, error);
        JSONObject result = response.getJSONObject("result");
        JSONObject location = result == null ? null : result.getJSONObject("location");
        if (location == null || location.getString("lat") == null || location.getString("lng") == null) {
            throw new OrderBusinessException(error);
        }
        return location.getString("lat") + "," + location.getString("lng");
    }

    /**
     * 发送地图请求并校验响应状态，将网络或解析失败统一转为业务异常
     * @param uri 请求地址及参数
     * @param error 请求失败时的业务提示
     * @return 地图接口响应数据
     */
    private JSONObject request(URI uri, String error) {
        try {
            JSONObject response = JSON.parseObject(restTemplate.getForObject(uri, String.class));
            if (response == null || !Integer.valueOf(0).equals(response.getInteger("status"))) {
                throw new OrderBusinessException(error);
            }
            return response;
        } catch (Exception ex) {
            // 不把包含 AK 的请求 URL 或上游响应暴露给客户端。
            throw new OrderBusinessException(error);
        }
    }
}
