package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.PromptDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Prompt {

    /**
     * Prompt name
     */
    private String promptKey;

    /**
     * PromptDescription
     */
    private String promptDescription;

    /**
     * latest version
     */
    private String latestVersion;

    /**
     * Latest version status: pre-pre-release version, release-official version
     */
    private String latestVersionStatus;

    /**
     * tags, comma separated
     */
    private String tags;

    /**
     * Prompt creation time, timestamp milliseconds
     */
    private Long createTime;

    /**
     * Prompt change time, timestamp milliseconds
     */
    private Long updateTime;

    /**
     * Convert from DO to DTO
     */
    public static Prompt fromDO(PromptDO promptDO) {
        return fromDO(promptDO, null);
    }

    /**
     * Convert from DO to DTO with latest version status
     */
    public static Prompt fromDO(PromptDO promptDO, String latestVersionStatus) {
        if (promptDO == null) {
            return null;
        }
        return Prompt.builder()
                .promptKey(promptDO.getPromptKey())
                .promptDescription(promptDO.getPromptDesc())
                .latestVersion(promptDO.getLatestVersion())
                .latestVersionStatus(latestVersionStatus)
                .tags(promptDO.getTags())
                .createTime(promptDO.getCreateTime() != null ? 
                    promptDO.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                .updateTime(promptDO.getUpdateTime() != null ? 
                    promptDO.getUpdateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() : null)
                .build();
    }
}
