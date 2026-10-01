package com.sky.mapper;

import com.sky.dto.OrdersPageQueryDTO;
import com.sky.entity.ShoppingCart;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/** 订单及购物车映射测试，解析MyBatis XML并验证生成的SQL和参数。 */
class OrderMapperTest {
    private Configuration load(String resource) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration;
    }

    /** 验证分页SQL只查询订单表，并支持用户、状态、模糊搜索及单侧时间过滤。 */
    @Test
    void paginationSqlFiltersOrdersWithoutJoiningDetails() throws Exception {
        Configuration config = load("mapper/OrderMapper.xml");
        OrdersPageQueryDTO dto = new OrdersPageQueryDTO();
        dto.setUserId(7L);
        dto.setStatus(5);
        dto.setNumber("123");
        dto.setPhone("138");
        dto.setBeginTime(LocalDateTime.of(2026, 10, 1, 0, 0));
        String sql = config.getMappedStatement("com.sky.mapper.OrderMapper.pageQuery").getBoundSql(dto).getSql();
        assertTrue(sql.contains("user_id = ?"));
        assertTrue(sql.contains("status = ?"));
        assertTrue(sql.contains("number like concat"));
        assertTrue(sql.contains("phone like concat"));
        assertTrue(sql.contains("order_time >= ?"));
        assertFalse(sql.contains("join"));
        assertFalse(sql.contains("order_time <= ?"));
        dto.setBeginTime(null);
        dto.setEndTime(LocalDateTime.of(2026, 10, 1, 0, 0));
        sql = config.getMappedStatement("com.sky.mapper.OrderMapper.pageQuery").getBoundSql(dto).getSql();
        assertTrue(sql.contains("order_time <= ?"));
        assertFalse(sql.contains("order_time >= ?"));
    }

    /** 验证购物车批量插入为每条商品绑定完整参数。 */
    @Test
    void batchCartInsertBindsEveryItem() throws Exception {
        Configuration config = load("mapper/ShoppingCartMapper.xml");
        Map<String, Object> params = new HashMap<>();
        params.put("shoppingCartList", Arrays.asList(new ShoppingCart(), new ShoppingCart()));
        assertEquals(18, config.getMappedStatement("com.sky.mapper.ShoppingCartMapper.insertBatch")
                .getBoundSql(params).getParameterMappings().size());
    }
}
