package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface EvaluatorMapper {

    /**
     * Create evaluator
     *
     * @param evaluator evaluator entity
     * @return Number of rows affected
     */
    int insert(EvaluatorDO evaluator);

    /**
     * Remove evaluator based on ID (tombstone)
     *
     * @param id evaluator ID
     * @return Number of rows affected
     */
    int deleteById(@Param("id") Long id);

    /**
     * Get evaluator based on ID
     *
     * @param id evaluator ID
     * @return evaluator entity
     */
    EvaluatorDO selectById(@Param("id") Long id);

    /**
     * Paginated query evaluator list
     *
     * @param name evaluator name (fuzzy query)
     * @param offset offset
     * @param limit limit quantity
    * @return evaluator list
     */
    List<EvaluatorDO> selectList(@Param("name") String name, 
                                @Param("offset") long offset, 
                                @Param("limit") int limit);

    /**
     * Number of statistical evaluators
     *
     * @param name evaluator name (fuzzy query)
     * @return the number of evaluators
     */
    int count(@Param("name") String name);

    /**
     * Update evaluator
     *
     * @param evaluator evaluator entity
     * @return Number of rows affected
     */
    int update(EvaluatorDO evaluator);
}
