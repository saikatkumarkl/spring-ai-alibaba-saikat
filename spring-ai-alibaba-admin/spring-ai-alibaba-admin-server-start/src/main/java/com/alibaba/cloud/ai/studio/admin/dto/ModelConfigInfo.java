package com.alibaba.cloud.ai.studio.admin.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class ModelConfigInfo {

    /**
     * Model configuration ID (required field)
     */
    @JsonProperty("modelId")
    private Long modelId;

    /**
     * Dynamic parameter storage
     * Store all model parameters except modelId
     */
    @JsonIgnore
    private Map<String, Object> parameters = new HashMap<>();

    /**
     * Jackson handles unknown attributes when deserializing
     * Store all properties except modelId into parameters
     */
    @JsonAnySetter
    private void setDynamicProperty(String key, Object value) {
        if (!"modelId".equals(key)) {
            parameters.put(key, value);
        }
    }

    /**
     * Output dynamic properties during Jackson serialization
     */
    @JsonAnyGetter
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * Get the specified parameter value
     *
     * @param parameterName parameter name
     * @return parameter value
     */
    public Object getParameter(String parameterName) {
        return parameters.get(parameterName);
    }

    /**
     * Get the specified parameter value (specified type)
     *
     * @param parameterName parameter name
     * @param type expected type
     * @param <T> type parameter
     * @return parameter value
     */
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String parameterName, Class<T> type) {
        Object value = parameters.get(parameterName);
        if (value == null) {
            return null;
        }
        
        try {
            return (T) value;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(
                String.format("参数 %s 的值 %s 无法转换为类型 %s", 
                    parameterName, value, type.getSimpleName()), e);
        }
    }

    /**
     * Set parameter value
     *
     * @param parameterName parameter name
     * @param value parameter value
     */
    public void setParameter(String parameterName, Object value) {
        parameters.put(parameterName, value);
    }

    /**
     * Check whether the specified parameters are included
     *
     * @param parameterName parameter name
     * @return whether it contains
     */
    public boolean hasParameter(String parameterName) {
        return parameters.containsKey(parameterName);
    }

    /**
     * Get all parameters
     *
     * @return parameter Map
     */
    public Map<String, Object> getAllParameters() {
        return parameters;
    }
}
