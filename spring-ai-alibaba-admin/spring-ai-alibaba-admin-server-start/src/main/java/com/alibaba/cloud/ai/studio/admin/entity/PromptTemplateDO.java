package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "prompt_build_template")
public class PromptTemplateDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Prompt template key
     */
    private String promptTemplateKey;

    /**
     * tags, comma separated
     */
    private String tags;

    /**
     * Template description
     */
    private String templateDesc;

    /**
     * Prompt template content
     */
    @Column(columnDefinition = "LONGTEXT")
    private String template;

    /**
     * Variable parameters in Prompt template
     */
    private String variables;

    /**
     * Recommended model parameters, JSON format
     */
    @Column(columnDefinition = "TEXT")
    private String modelConfig;
}
