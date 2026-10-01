package com.sky.service.impl;

import com.sky.exception.OrderBusinessException;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.containsString;

/** 配送范围测试，模拟百度地图响应，不访问真实地图服务。 */
class DeliveryRangeServiceImplTest {
    private DeliveryRangeServiceImpl service;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        service = new DeliveryRangeServiceImpl();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "maxDistance", 5000);
        ReflectionTestUtils.setField(service, "shopAddress", "北京市海淀区店铺");
        ReflectionTestUtils.setField(service, "ak", "test-ak");
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
    }

    private void coordinates() {
        String response = "{\"status\":0,\"result\":{\"location\":{\"lat\":39.9,\"lng\":116.3}}}";
        server.expect(requestTo(containsString("geocoding/v3/"))).andRespond(withSuccess(response, APPLICATION_JSON));
        server.expect(requestTo(containsString("geocoding/v3/"))).andRespond(withSuccess(response, APPLICATION_JSON));
    }

    /** 验证本地模拟配送模式直接放行，不发送地图请求。 */
    @Test
    void disabledCheckMakesNoRequests() {
        ReflectionTestUtils.setField(service, "enabled", false);
        service.check("");
        server.verify();
    }

    /** 验证真实配送模式缺少AK时在发送请求前报错。 */
    @Test
    void missingConfigurationFailsBeforeRequest() {
        ReflectionTestUtils.setField(service, "ak", "");
        assertThrows(OrderBusinessException.class, () -> service.check("收货地址"));
        server.verify();
    }

    /** 验证配送距离恰好为5000米时允许下单。 */
    @Test
    void exactlyFiveKilometersIsAllowed() {
        coordinates();
        server.expect(requestTo(containsString("directionlite/v1/driving")))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[{\"distance\":5000}]}}", APPLICATION_JSON));
        service.check("收货地址");
        server.verify();
    }

    /** 验证配送距离超过5000米时拒绝下单。 */
    @Test
    void longerRouteIsRejected() {
        coordinates();
        server.expect(requestTo(containsString("directionlite/v1/driving")))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[{\"distance\":5001}]}}", APPLICATION_JSON));
        assertEquals("超出配送范围", assertThrows(OrderBusinessException.class, () -> service.check("收货地址")).getMessage());
        server.verify();
    }

    /** 验证地图返回空路线时转换为业务异常。 */
    @Test
    void emptyRoutesProduceBusinessError() {
        coordinates();
        server.expect(requestTo(containsString("directionlite/v1/driving")))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[]}}", APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.check("收货地址"));
        server.verify();
    }

    /** 验证地图返回无效数据时转换为业务异常。 */
    @Test
    void malformedMapResponseProducesBusinessError() {
        server.expect(requestTo(containsString("geocoding/v3/"))).andRespond(withSuccess("invalid json", APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.check("收货地址"));
        server.verify();
    }
}
