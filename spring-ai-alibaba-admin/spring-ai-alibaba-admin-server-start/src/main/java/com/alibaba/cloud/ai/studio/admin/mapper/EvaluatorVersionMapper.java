package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface EvaluatorVersionMapper {

    /**
     * Create evaluator version
     *
     * @param evaluatorVersion evaluator version entity
     * @return Number of rows affected
     */
    int insert(EvaluatorVersionDO evaluatorVersion);

    /**
     * Remove evaluator version based on ID (tombstone)
     *
     * @param id evaluator version ID
     * @return Number of rows affected
     */
    int deleteById(@Param("id") Long id);

    /**
     * Get evaluator version by ID
     *
     * @param id evaluator version ID
     * @return evaluator version entity
     */
    EvaluatorVersionDO selectById(@Param("id") Long id);

    /**
     * Get a list of evaluator versions based on evaluator ID
     *
     * @param evaluatorId evaluator ID
     * @param name evaluator version name (fuzzy query)
     * @param offset offset
     * @param limit limit quantity
     * @return list of evaluator versions
     */
    List<EvaluatorVersionDO> selectListByEvaluatorId(@Param("evaluatorId") Long evaluatorId,
                                                     @Param("name") String name,
                                                     @Param("offset") long offset,
                                                     @Param("limit") int limit);


    /**
     * Get a list of evaluator versions based on evaluator ID
     *
     * @param evaluatorId evaluator ID
     * @return list of evaluator versions
     */
    EvaluatorVersionDO selectLatestVersionByEvaluatorId(@Param("evaluatorId") Long evaluatorId);

    /**
     * Count the number of evaluator versions based on evaluator ID
     *
     * @param evaluatorId evaluator ID
     * @param name evaluator version name (fuzzy query)
     * @return the number of evaluator versions
     */
    int countByEvaluatorId(@Param("evaluatorId") Long evaluatorId,
                           @Param("name") String name);



    int update(@Param("id") Long id,
               @Param("description") String description,
               @Param("status") String status);


}
