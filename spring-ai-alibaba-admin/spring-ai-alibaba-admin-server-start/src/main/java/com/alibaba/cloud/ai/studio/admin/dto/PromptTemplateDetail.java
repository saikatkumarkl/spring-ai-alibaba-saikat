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
public class PromptTemplateDetail {

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
     * Prompt content
     */
    private String template;

    /**
     * Variable value in Prompt, JSON
     */
    private String variables;

    /**
     * Recommended model related parameters, JSON
     */
    private String modelConfig;

    /**
     * Convert from DO to DTO
     */
    public static PromptTemplateDetail fromDO(PromptTemplateDO promptTemplateDO) {
        if (promptTemplateDO == null) {
            return null;
        }
        return PromptTemplateDetail.builder()
                .promptTemplateKey(promptTemplateDO.getPromptTemplateKey())
                .templateDescription(promptTemplateDO.getTemplateDesc())
                .tags(promptTemplateDO.getTags())
                .template(promptTemplateDO.getTemplate())
                .variables(promptTemplateDO.getVariables())
                .modelConfig(promptTemplateDO.getModelConfig())
                .build();
    }
}
