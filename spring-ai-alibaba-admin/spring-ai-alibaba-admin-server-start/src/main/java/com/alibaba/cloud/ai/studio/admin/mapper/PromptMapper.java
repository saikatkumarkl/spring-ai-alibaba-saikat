package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.PromptDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

@Mapper
public interface PromptMapper {

    /**
     * CreatePrompt
     *
     * @param prompt Prompt entity
     * @return Number of rows affected
     */
    int insert(PromptDO prompt);

    /**
     * Delete Prompt based on Prompt Key (logical deletion)
     *
     * @param promptKey Prompt Key
     * @return Number of rows affected
     */
    int deleteByPromptKey(@Param("promptKey") String promptKey);

    /**
     * Get Prompt based on Prompt Key
     *
     * @param promptKey Prompt Key
     * @return Prompt entity
     */
    PromptDO selectByPromptKey(@Param("promptKey") String promptKey);

    /**
     * Query prompt list
     *
     * @param search query mode
     * @param tag tag
     * @param promptKey Prompt Key
     * @param offset offset
     * @param limit quantity limit
     * @return Prompt list
     */
    List<PromptDO> selectList(@Param("search") String search,
                              @Param("tag") String tag,
                              @Param("promptKey") String promptKey,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    /**
     * Query the total number of prompts
     *
     * @param search query mode
     * @param tag tag
     * @param promptKey Prompt Key
     * @return total number
     */
    int selectCount(@Param("search") String search,
                    @Param("tag") String tag,
                    @Param("promptKey") String promptKey);

    /**
     * UpdatePrompt
     *
     * @param prompt Prompt entity
     * @return Number of rows affected
     */
    int update(PromptDO prompt);

    /**
     * Update to latest version
     *
     * @param promptKey     Prompt Key
     * @param latestVersion latest version
     * @return Number of rows affected
     */
    int updateLatestVersion(@Param("promptKey") String promptKey,
                            @Param("latestVersion") String latestVersion);

    /**
     * Get Prompt and its latest version status based on Prompt Key
     *
     * @param promptKey Prompt Key
     * @return Map contains prompt information and latest version status
     */
    Map<String, Object> selectByPromptKeyWithLatestVersionStatus(@Param("promptKey") String promptKey);

    /**
     * Query the Prompt list and its latest version status
     *
     * @param search query mode
     * @param tag tag
     * @param promptKey Prompt Key
     * @param offset offset
     * @param limit quantity limit
     * @return Map list contains Prompt information and latest version status
     */
    List<Map<String, Object>> selectListWithLatestVersionStatus(@Param("search") String search,
                                                                                     @Param("tag") String tag,
                                                                                     @Param("promptKey") String promptKey,
                                                                                     @Param("offset") int offset,
                                                                                     @Param("limit") int limit);
}
