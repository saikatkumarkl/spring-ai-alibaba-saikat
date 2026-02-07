package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.PromptVersionDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptVersionDetail {

    /**
     * version number
     */
    private String version;

    /**
     * Prompt name
     */
    private String promptKey;

    /**
     * Prompt version description
     */
    private String versionDescription;

    /**
     * Prompt content
     */
    private String template;

    /**
     * Variable value in Prompt, JSON
     */
    private String variables;

    /**
     * Model related parameters used, JSON
     */
    private String modelConfig;

    /**
     * Prompt version creation time, timestamp milliseconds
     */
    private Long createTime;

    /**
     * Previous version
     */
    private String previousVersion;

    /**
     * Version status: pre-pre-release version, release-official version
     */
    private String status;

    /**
     * Convert from DO to DTO
     */
    public static PromptVersionDetail fromDO(PromptVersionDO promptVersionDO) {
        if (promptVersionDO == null) {
            return null;
        }
                return PromptVersionDetail.builder()
                .version(promptVersionDO.getVersion())
                .promptKey(promptVersionDO.getPromptKey())
                .versionDescription(promptVersionDO.getVersionDesc())
                .template(promptVersionDO.getTemplate())
                .variables(promptVersionDO.getVariables())
                .modelConfig(promptVersionDO.getModelConfig())
                .createTime(promptVersionDO.getCreateTime() != null ?
                    promptVersionDO.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                .previousVersion(promptVersionDO.getPreviousVersion())
                .status(promptVersionDO.getStatus())
                .build();
    }
}
