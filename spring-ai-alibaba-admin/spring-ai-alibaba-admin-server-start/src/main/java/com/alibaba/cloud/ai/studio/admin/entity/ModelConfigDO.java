package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "model_config")
public class ModelConfigDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Model name
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * Provider (openai, azure, etc)
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * Model identifier (gpt-4, gpt-3.5-turbo, etc.)
     */
    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    /**
     * Model service address
     */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /**
     * API key
     */
    @Column(name = "api_key", nullable = false, length = 500)
    private String apiKey;

    /**
     * Default parameter configuration (JSON format)
     */
    @Column(name = "default_parameters", columnDefinition = "JSON")
    private String defaultParameters;

    /**
     * Supported parameter definitions (JSON format)
     */
    @Column(name = "supported_parameters", columnDefinition = "JSON")
    private String supportedParameters;

    /**
     * Status: 1-enabled, 0-disabled
     */
    @Builder.Default
    private Integer status = 1;

    /**
     * creation time
     */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /**
     * Update time
     */
    @Column(name = "update_time")
    private LocalDateTime updateTime;
}
