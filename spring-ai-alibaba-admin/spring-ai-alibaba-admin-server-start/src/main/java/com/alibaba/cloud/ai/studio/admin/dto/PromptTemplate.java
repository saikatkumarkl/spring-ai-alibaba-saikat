package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.PromptTemplateDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptTemplate {

    /**
     * Prompt template name
     */
    private String promptTemplateKey;

    /**
     * PromptDescription
     */
    private String templateDescription;

    /**
     * tags, comma separated
     */
    private String tags;

    /**
     * Convert from DO to DTO
     */
    public static PromptTemplate fromDO(PromptTemplateDO promptTemplateDO) {
        if (promptTemplateDO == null) {
            return null;
        }
        return PromptTemplate.builder()
                .promptTemplateKey(promptTemplateDO.getPromptTemplateKey())
                .templateDescription(promptTemplateDO.getTemplateDesc())
                .tags(promptTemplateDO.getTags())
                .build();
    }
}
