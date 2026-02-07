package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.ExperimentResultDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ExperimentResultMapper {

    /**
     * Create experimental results in batches
     *
     * @param experimentResults Experiment result entity list
     * @return Number of rows affected
     */
    int batchInsert(@Param("experimentResults") List<ExperimentResultDO> experimentResults);

    /**
     * Delete experiment results based on experiment ID
     *
     * @param experimentId experiment ID
     * @return Number of rows affected
     */
    int deleteByExperimentId(@Param("experimentId") Long experimentId);

    /**
     * Get experimental results based on ID
     *
     * @param id Experiment result ID
     * @return experimental result entity
     */
    ExperimentResultDO selectById(@Param("id") Long id);



    /**
     * Get the number of experiment results based on the experiment ID
     *
     * @param experimentId experiment ID
     * @return the number of experimental results
     */
    int selectCountByExperimentIdAndEvaluator(@Param("experimentId") Long experimentId,
                                              @Param("evaluatorVersionId") Long evaluatorVersionId);

    /**
     * Query experimental results
     *
     * @param experimentId experiment ID
     * @return list of experimental results
     */
    List<ExperimentResultDO> selectByExperimentAndEvaluator(
            @Param("experimentId") Long experimentId,
            @Param("evaluatorVersionId") Long evaluatorVersionId);



    /**
     * Query experimental results by page
     *
     * @param experimentId experiment ID
     * @param offset offset
     * @param limit limit quantity
     * @return list of experimental results
     */
    List<ExperimentResultDO> selectByExperimentAndEvaluatorWithPageble(
            @Param("experimentId") Long experimentId,
            @Param("evaluatorVersionId") Long evaluatorVersionId,
            @Param("offset") long offset,
            @Param("limit") int limit);




    /**
     * Update experiment results based on ID
     *
     * @param experimentResult Experiment result entity
     * @return Number of rows affected
     */
    int updateById(ExperimentResultDO experimentResult);


}
