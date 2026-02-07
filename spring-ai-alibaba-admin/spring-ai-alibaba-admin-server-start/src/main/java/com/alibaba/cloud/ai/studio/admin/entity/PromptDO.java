package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt")
public class PromptDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Prompt Key
     */
    private String promptKey;

    /**
     * PromptDescription
     */
    private String promptDesc;

    /**
     * latest version
     */
    private String latestVersion;

    /**
     * tags, comma separated
     */
    private String tags;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;
}
