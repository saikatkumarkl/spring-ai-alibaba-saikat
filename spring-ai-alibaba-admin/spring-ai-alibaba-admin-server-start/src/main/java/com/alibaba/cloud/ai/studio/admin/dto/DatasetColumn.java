package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.Data;

@Data
public class DatasetColumn {

    /**
     * Column name
     */
    private String name;

    /**
     * Data types: STRING, NUMBER, BOOLEAN, JSON, ARRAY
     */
    private String dataType;

    /**
     * Display format: PLAIN_TEXT, MARKDOWN, CODE, JSON, TABLE
     */
    private String displayFormat;

    /**
     * Column description
     */
    private String description;

    /**
     * Is it required?
     */
    private Boolean required;
} 
