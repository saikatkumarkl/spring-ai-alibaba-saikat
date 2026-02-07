package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Builder
@Data
public class EvaluatorVersion {
    /**
     * Primary key ID
     */
    private Long id;


    /**
     * Evaluator version description
     */
    private String description;

    /**
     * version number
     */
    private String version;

    /**
     * Model ID
     */
    private String modelConfig;

    /**
     * Prompt configuration (JSON format)
     */
    private String prompt;

    /**
     * Variable parameters in evaluators
     */
    private String variables;

    /**
     * version status
     */
    private String status;


    /**
     * Experimental collection (one-to-many relationship)
     */
    private String experiments;



    /**
     * creation time
     */

    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;

    /**
     * Convert from DO object to DTO object
     *
     * @param evaluatorVersionDO DO object
     * @return DTO object
     */
    public static EvaluatorVersion fromDO(EvaluatorVersionDO evaluatorVersionDO) {
        if (evaluatorVersionDO == null) {
            return null;
        }
        return EvaluatorVersion.builder()
                .id(evaluatorVersionDO.getId())
                .description(evaluatorVersionDO.getDescription())
                .version(evaluatorVersionDO.getVersion())
                .modelConfig(evaluatorVersionDO.getModelConfig())
                .prompt(evaluatorVersionDO.getPrompt())
                .createTime(evaluatorVersionDO.getCreateTime())
                .updateTime(evaluatorVersionDO.getUpdateTime())
                .status(evaluatorVersionDO.getStatus())
                .experiments(evaluatorVersionDO.getExperiments())
                .build();
    }
}
