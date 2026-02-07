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
@Table(name = "prompt_version")
public class PromptVersionDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * version number
     */
    private String version;

    /**
     * Prompt Key
     */
    private String promptKey;

    /**
     * Version description
     */
    private String versionDesc;

    /**
     * Prompt template content
     */
    @Column(columnDefinition = "LONGTEXT")
    private String template;

    /**
     * Variable parameters in Prompt template, JSON format
     */
    private String variables;

    /**
     * Debugging the model parameters of the prompt, JSON format
     */
    private String modelConfig;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Pre-version for comparison
     */
    private String previousVersion;

    /**
     * Version status: pre-pre-release version, release-official version
     */
    @Builder.Default
    private String status = "pre";
}
