package com.atguigu.gmall.product.mapper;

import com.atguigu.gmall.model.product.BaseAttrInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * @author luheng
 * @create 2020-11-30 17:47
 * @param:
 */
//所有平台属性interface
@Mapper
public interface BaseAttrInfoMapper extends BaseMapper<BaseAttrInfo> {

    List<BaseAttrInfo> selectBaseAttrInfoList(@Param("category1Id") Long category1Id,
                                              @Param("category2Id") Long category2Id,
                                              @Param("category3Id") Long category3Id);

    /**
     * 通过skuId 集合来查询数据【平台属性+平台属性值】
     * @param skuId
     * @return
     */
    List<BaseAttrInfo> selectBaseAttrInfoListBySkuId(Long skuId);
}
