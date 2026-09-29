package com.sky.mapper;

import com.sky.entity.ShoppingCart;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ShoppingCartMapper {

    /**
     * 查询购物车商品信息
     * @param shoppingCart
     * @return
     */
    List<ShoppingCart> list(ShoppingCart shoppingCart);

    /**
     * 更新购物车商品数量信息
     * @param cart
     */
    @Update("update shopping_cart set number = #{number} where id = #{id}")
    void update(ShoppingCart cart);

    /**
     * 插入购物车商品信息
     * @param shoppingCart
     */
    @Insert("insert into shopping_cart(user_id, dish_id, setmeal_id, name, image, amount, number, create_time,dish_flavor) " +
            "values(#{userId}, #{dishId}, #{setmealId}, #{name}, #{image}, #{amount}, #{number}, #{createTime},#{dishFlavor})")
    void insert(ShoppingCart shoppingCart);

    /**
     * 删除购物车商品信息
     * @param userId
     * @return
     */
    @Select("select * from shopping_cart where user_id = #{userId} order by create_time asc")
    List<ShoppingCart> getById(Long userId);

    @Delete("delete from shopping_cart where user_id = #{userId}")
    void deleteAll(Long userId);

    void deleteById(ShoppingCart shoppingCart);
}
