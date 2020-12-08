package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseCategory2;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author luheng
 * @create 2020-11-30 17:46
 * @param:
 */
//所有一级分类下的所有二级分类
@Mapper
public interface BaseCategory2Mapper extends BaseMapper<BaseCategory2> {
}
