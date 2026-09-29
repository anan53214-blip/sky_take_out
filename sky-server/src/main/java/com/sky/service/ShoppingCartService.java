package com.sky.service;

import com.sky.dto.ShoppingCartDTO;
import com.sky.entity.ShoppingCart;

import java.util.List;

public interface ShoppingCartService {

    /**
     * 添加购物车
     * @param shoppingCartDTO
     * @return
     */
    void add(ShoppingCartDTO shoppingCartDTO);

    /**
     * 查看购物车商品
     * @return
     */
    List<ShoppingCart> showList();

    /**
     * 清空购物车
     */
    void clean();

    /**
     * 删除购物车一个商品
     */
    void delete(ShoppingCartDTO shoppingCartDTO);
}
