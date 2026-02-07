package com.alibaba.cloud.ai.studio.admin.mapper;

import com.alibaba.cloud.ai.studio.admin.entity.PromptTemplateDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PromptTemplateMapper {

    /**
     * Get the Prompt template based on the template Key
     *
     * @param promptTemplateKey template Key
     * @return Prompt template entity
     */
    PromptTemplateDO selectByPromptTemplateKey(@Param("promptTemplateKey") String promptTemplateKey);

    /**
     * Query Prompt template list
     *
     * @param search query mode
     * @param tag tag
     * @param promptTemplateKey template Key
     * @param offset offset
     * @param limit quantity limit
     * @return Prompt template list
     */
    List<PromptTemplateDO> selectList(@Param("search") String search,
                                      @Param("tag") String tag,
                                      @Param("promptTemplateKey") String promptTemplateKey,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    /**
     * Query the total number of Prompt templates
     *
     * @param search query mode
     * @param tag tag
     * @param promptTemplateKey template Key
     * @return total number
     */
    int selectCount(@Param("search") String search,
                    @Param("tag") String tag,
                    @Param("promptTemplateKey") String promptTemplateKey);
}
