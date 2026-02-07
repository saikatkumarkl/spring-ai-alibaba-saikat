package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.PromptVersionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromptVersionMapper {

    /**
     * Create prompt version
     *
     * @param promptVersion Prompt version entity
     * @return Number of rows affected
     */
    int insert(PromptVersionDO promptVersion);

    /**
     * Get the Prompt version based on the Prompt Key and version
     *
     * @param promptKey Prompt Key
     * @param version version number
     * @return Prompt version entity
     */
    PromptVersionDO selectByPromptKeyAndVersion(@Param("promptKey") String promptKey,
                                                @Param("version") String version);

    /**
     * Query Prompt version list
     *
     * @param promptKey Prompt Key
     * @param status version status
     * @param offset offset
     * @param limit quantity limit
     * @return Prompt version list
     */
    List<PromptVersionDO> selectListByPromptKey(@Param("promptKey") String promptKey,
                                                @Param("status") String status,
                                                @Param("offset") int offset,
                                                @Param("limit") int limit);

    /**
     * Query the total number of Prompt versions
     *
     * @param promptKey Prompt Key
     * @param status version status
     * @return total number
     */
    int selectCountByPromptKey(@Param("promptKey") String promptKey,
                               @Param("status") String status);

    /**
     * Get the latest version number
     *
     * @param promptKey Prompt Key
     * @return latest version number
     */
    String selectLatestVersion(@Param("promptKey") String promptKey);

    /**
     * Check if version exists
     *
     * @param promptKey Prompt Key
     * @param version version number
     * @return does it exist
     */
    boolean existsByPromptKeyAndVersion(@Param("promptKey") String promptKey,
                                        @Param("version") String version);

    /**
     * Get status based on Prompt Key and version
     *
     * @param promptKey Prompt Key
     * @param version version number
     * @return version status
     */
    String selectStatusByPromptKeyAndVersion(@Param("promptKey") String promptKey,
                                           @Param("version") String version);

    /**
     * Update prompt version
     *
     * @param promptVersion Prompt version entity
     * @return Number of rows affected
     */
    int updateByPromptKeyAndVersion(PromptVersionDO promptVersion);

    /**
     * Delete all versions based on Prompt Key
     *
     * @param promptKey Prompt Key
     * @return Number of rows affected
     */
    int deleteByPromptKey(@Param("promptKey") String promptKey);
}
