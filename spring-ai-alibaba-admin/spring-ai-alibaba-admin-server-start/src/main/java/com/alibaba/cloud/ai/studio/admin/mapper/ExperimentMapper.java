package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.ExperimentDO;
import com.alibaba.cloud.ai.studio.admin.entity.ExperimentResultDO;
import com.alibaba.cloud.ai.studio.admin.enums.ExperimentStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface ExperimentMapper {

    /**
     * Create an experiment
     *
     * @param experiment experimental entity
     * @return Number of rows affected
     */
    int insert(ExperimentDO experiment);

    /**
     * Delete experiment by ID
     *
     * @param id experiment ID
     * @return Number of rows affected
     */
    int deleteById(@Param("id") Long id);

    /**
     * Get experiment by ID
     *
     * @param id experiment ID
     * @return experimental entity
     */
    ExperimentDO selectById(@Param("id") Long id);

    /**
     * Query the experiment list by page
     *
     * @param name Experiment name (fuzzy query)
     * @param status Experiment status
     * @param offset offset
     * @param limit limit quantity
     * @return list of experiments
     */
    List<ExperimentDO> selectList(@Param("name") String name,
                                  @Param("status") ExperimentStatus status,
                                  @Param("offset") long offset,
                                  @Param("limit") int limit);

    /**
     * Count the number of experiments
     *
     * @param name Experiment name (fuzzy query)
     * @param status Experiment status
     * @return number of experiments
     */
    int count(@Param("name") String name,
              @Param("status") ExperimentStatus status);

    /**
     * Update experiment based on ID
     *
     * @param experiment experimental entity
     * @return Number of rows affected
     */
    int updateById(ExperimentDO experiment);

    /**
     * Create experiment results
     *
     * @param experimentResult Experiment result entity
     * @return Number of rows affected
     */
    int insertResult(ExperimentResultDO experimentResult);


    /**
     * Get experimental results based on datasetID
     *
     * @param datasetId Experiment result ID
     * @return experimental result entity
     */
    List<ExperimentDO> selectByDatasetId(@Param("datasetId") Long datasetId,
                                               @Param("offset") long offset,
                                               @Param("limit") int limit);

    int selectCountByDatasetId(@Param("datasetId") Long datasetId);

    /**
     * Query the experiment list by page based on the evaluator ID
     *
     * @param evaluatorId evaluator ID
     * @param offset offset
     * @param limit limit quantity
     * @return list of experiments
     */
    List<ExperimentDO> selectByEvaluatorId(@Param("evaluatorId") Long evaluatorId,
                                           @Param("offset") long offset,
                                           @Param("limit") int limit);

    /**
     * Query the experiment list by page based on the evaluator version ID
     *
     * @param evaluatorVersionId evaluator version ID
     * @param offset offset
     * @param limit limit quantity
     * @return list of experiments
     */
    List<ExperimentDO> selectByEvaluatorVersionId(@Param("evaluatorVersionId") Long evaluatorVersionId,
                                                  @Param("offset") long offset,
                                                  @Param("limit") int limit);

    /**
     * Count the number of experiments using the specified evaluator
     *
     * @param evaluatorId evaluator ID
     * @return number of experiments
     */
    int selectCountByEvaluatorId(@Param("evaluatorId") Long evaluatorId);

    /**
     * Counts the number of experiments using a specified evaluator version
     *
     * @param evaluatorVersionId evaluator version ID
     * @return number of experiments
     */
    int selectCountByEvaluatorVersionId(@Param("evaluatorVersionId") Long evaluatorVersionId);

}
