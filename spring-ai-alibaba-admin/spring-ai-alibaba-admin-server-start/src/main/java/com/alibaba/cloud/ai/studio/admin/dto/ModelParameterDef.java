package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.Data;

@Data
public class ModelParameterDef {

    /**
     * Parameter name
     */
    private String name;

    /**
     * Parameter type: number, string, boolean
     */
    private String type;

    /**
     * default value
     */
    private Object defaultValue;

    /**
     * Minimum value (numeric type)
     */
    private Object minValue;

    /**
     * Maximum value (numeric type)
     */
    private Object maxValue;

    /**
     * Parameter description
     */
    private String description;

    /**
     * Is it required?
     */
    private Boolean required;
}
