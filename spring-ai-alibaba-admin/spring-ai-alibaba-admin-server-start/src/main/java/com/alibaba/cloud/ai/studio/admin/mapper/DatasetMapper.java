package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.DatasetDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;


import java.util.List;


@Mapper
public interface DatasetMapper {

    /**
     * Create a review set
     *
     * @param dataset evaluation set entity
     * @return Number of rows affected
     */
    int insert(DatasetDO dataset);

    /**
     * Delete a review set based on ID (logical deletion)
     *
     * @param id evaluation set ID
     * @return Number of rows affected
     */
    int deleteById(@Param("id") Long id);

    /**
     * Get the evaluation set based on ID
     *
     * @param id evaluation set ID
     * @return evaluation set entity
     */
    DatasetDO selectById(@Param("id") Long id);

    /**
     * Query the evaluation set list
     *
     * @param name Evaluation set name (fuzzy query)
     * @param offset offset
     * @param limit quantity limit
     * @return evaluation set list
     */
    List<DatasetDO> selectList(@Param("name") String name,
                               @Param("offset") Long offset,
                               @Param("limit") int limit);

    /**
     * Query the total number of evaluation sets
     *
     * @param name Evaluation set name (fuzzy query)
     * @return total number
     */
    int selectCount(@Param("name") String name);

    /**
     * Update review set
     *
     * @param id evaluation set ID
     * @param name evaluation set name
     * @param description evaluation set description
     * @return Number of rows affected
     */
    int update(@Param("id") Long id,
               @Param("name") String name,
               @Param("description") String description);
}
