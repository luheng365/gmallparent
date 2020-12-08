package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseAttrValue;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author luheng
 * @create 2020-11-30 17:48
 * @param:
 */
//所有平台属性值interface
@Mapper
public interface BaseAttrValueMapper extends BaseMapper<BaseAttrValue> {
}
