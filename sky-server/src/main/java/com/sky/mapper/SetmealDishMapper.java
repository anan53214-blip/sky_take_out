package com.sky.mapper;

import com.sky.entity.SetmealDish;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SetmealDishMapper {
    /**
     * 根据菜品id查询对应套餐id
     * @param dishIds
     * @return
     */
    //select setmeal_id from setmeal_dish where dish_id in (1,2,3,4)
    List<Long> getSetmealIdsByDishId(List<Long> dishIds);

    /**
     * 新增套餐
     * @param setmealDishes
     * @return
     */
    void insert(List<SetmealDish> setmealDishes);

    void deleteByIds(List<Long> setmealIds);
}
