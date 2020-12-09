package com.atguigu.gmall.list.service;

import com.atguigu.gmall.model.list.Goods;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * @author luheng
 * @create 2020-12-09 16:43
 * @param:
 */
public interface GoodsRepository extends ElasticsearchRepository<Goods,Long> {

}
