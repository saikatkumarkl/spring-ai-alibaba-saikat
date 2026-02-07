package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Builder
@AllArgsConstructor
public class Evaluator {

    /**
     * Primary key ID
     */
    private Long id;

    /**
     * evaluator name
     */
    private String name;


    /**
     * Evaluator description
     */
    private String description;


    private String modelConfig;

    private String latestVersion;


    private String variables;

    private String prompt;


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
     * @param evaluatorDO DO object
     * @return DTO object
     */
    public static Evaluator fromDO(EvaluatorDO evaluatorDO) {
        if (evaluatorDO == null) {
            return null;
        }
        return Evaluator.builder()
                .id(evaluatorDO.getId())
                .name(evaluatorDO.getName())
                .description(evaluatorDO.getDescription())
                .createTime(evaluatorDO.getCreateTime())
                .updateTime(evaluatorDO.getUpdateTime())
                .build();
    }
} 
