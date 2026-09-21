package com.sky.controller.admin;

import com.sky.dto.SetmealDTO;
import com.sky.dto.SetmealPageQueryDTO;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.SetmealService;
import com.sky.vo.SetmealVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/admin/setmeal")
@Slf4j
@Api(tags = "套餐相关接口")
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private RedisTemplate redisTemplate;
    /**
     * 新增套餐
     * @param setmealDTO
     * @return
     */
    @PostMapping
    @ApiOperation("新增套餐接口")
    @Cacheable(cacheNames = "setmealCache", key = "#setmealDTO.categoryId") //key:setmealCache::100
    public Result save(@RequestBody SetmealDTO setmealDTO) {
        log.info("新增套餐:{}",setmealDTO);
        setmealService.insert(setmealDTO);
        cleanCache("setmealCache*");
        return Result.success();
    }

    /**
     * 套餐分页查询
     * @param setmealPageQueryDTO
     * @return
     */
    @GetMapping("/page")
    @ApiOperation("套餐分页查询接口")
    public Result<PageResult> pageQuery(SetmealPageQueryDTO setmealPageQueryDTO){
        log.info("套餐分页查询:{}",setmealPageQueryDTO);
        PageResult pageResult= setmealService.pageQuery(setmealPageQueryDTO);
        return Result.success(pageResult);
    }

    /**
     * 批量删除套餐
     * @param ids
     * @return
     */
    @DeleteMapping
    @ApiOperation("批量删除套餐接口")
    @CacheEvict(cacheNames = "setmealCache",allEntries = true) //key:setmealCache::100
    public Result delele(@RequestParam List<Long> ids){
        log.info("批量删除套餐:{}",ids);
        setmealService.delete(ids);
        cleanCache("setmealCache*");
        return Result.success();
    }

    /**
     * 根据id查询套餐
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @ApiOperation("根据id查询套餐及菜品接口")
    public Result<SetmealVO> getByIdWithDish(@PathVariable Long id){
        log.info("根据id查询套餐及菜品:{}",id);
        SetmealVO setmealVO= setmealService.getByIdWithDish(id);
        return Result.success(setmealVO);
    }

    /**
     * 修改套餐信息
     * @param setmealDTO
     * @return
     */
    @PutMapping
    @ApiOperation("修改套餐信息接口")
    public Result update(@RequestBody SetmealDTO setmealDTO){
        log.info("修改套餐信息:{}",setmealDTO);
        setmealService.update(setmealDTO);
        cleanCache("setmealCache::"+setmealDTO.getCategoryId());
        return Result.success();
    }

    /**
     * 套餐起售，停售
     * @return
     */
    @PostMapping("/status/{status}")
    @ApiOperation("起售停售接口")
    public Result setStatus(@PathVariable Integer status,Long id){
        log.info("套餐起售停售:id为:{},状态为:{}",id,status);
        setmealService.setStatus(id,status);
        cleanCache("setmealCache*");
        return Result.success();
    }

    private void cleanCache(String pattern){
        Set keys = redisTemplate.keys(pattern);
        redisTemplate.delete(keys);
    }
}
