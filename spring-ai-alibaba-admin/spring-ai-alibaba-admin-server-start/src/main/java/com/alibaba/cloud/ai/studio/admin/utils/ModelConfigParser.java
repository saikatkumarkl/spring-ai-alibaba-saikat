package com.alibaba.cloud.ai.studio.admin.utils;

import com.alibaba.cloud.ai.studio.admin.dto.ModelConfigInfo;
import com.alibaba.cloud.ai.studio.admin.repository.ModelConfigRepository;
import com.alibaba.cloud.ai.studio.core.base.manager.ModelManager;
import com.alibaba.cloud.ai.studio.core.base.entity.ModelEntity;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConfigParser {
    
    private final ObjectMapper objectMapper;
    
    private final ModelConfigRepository modelConfigRepository;
    
    private final ModelManager modelManager;
    
    /**
     * Parse model configuration JSON string
     *
     * @param modelConfigJson model configuration JSON
     * @return model configuration information
     */
    public ModelConfigInfo parseModelConfig(String modelConfigJson) {
        if (!StringUtils.hasText(modelConfigJson)) {
            throw new IllegalArgumentException("Model configuration cannot be empty");
        }
        
        try {
            return objectMapper.readValue(modelConfigJson, ModelConfigInfo.class);
        } catch (Exception e) {
            log.error("Failed to parse model configuration JSON: {}", modelConfigJson, e);
            throw new IllegalArgumentException("Model configuration format error:" + e.getMessage(), e);
        }
    }
    
    public ModelConfigInfo checkAndGetModelConfigInfo(String modelConfig) {
        ModelConfigInfo modelConfigInfo = null;
        try {
            modelConfigInfo = parseModelConfig(modelConfig);
            validateModelConfig(modelConfigInfo);
            
            //Verify that the model configuration exists
            //First try to find from ModelConfigRepository (YAML file)
            boolean exists = modelConfigRepository.existsById(modelConfigInfo.getModelId());
            
            if (!exists) {
                //If it does not exist in ModelConfigRepository, try to find it from ModelManager (database)
                //Get workspaceId safely
                String workspaceId = null;
                try {
                    RequestContext context = RequestContextHolder.getRequestContext();
                    if (context != null) {
                        workspaceId = context.getWorkspaceId();
                    }
                } catch (Exception e) {
                    log.warn("Unable to obtain workspaceId of RequestContext, workspace filtering will not be used: {}", e.getMessage());
                }
                
                ModelEntity modelEntity = null;
                
                //1. First try to find by modelId (Long) as the id of ModelEntity
                if (modelConfigInfo.getModelId() != null) {
                    modelEntity = modelManager.findModelByIdOrName(modelConfigInfo.getModelId(), workspaceId);
                }
                
                //2. If not found by modelId, try to find by modelName
                if (modelEntity == null) {
                    String modelName = (String) modelConfigInfo.getParameter("modelName");
                    if (StringUtils.hasText(modelName)) {
                        modelEntity = modelManager.findModelByIdOrName(modelName, workspaceId);
                    }
                }
                
                //3. If still not found, throw an exception
                if (modelEntity == null) {
                    throw new IllegalArgumentException("Model configuration does not exist: modelId=" + modelConfigInfo.getModelId() + 
                        (modelConfigInfo.getParameter("modelName") != null ? ", modelName=" + modelConfigInfo.getParameter("modelName") : ""));
                }
                
                //If the model is found from the ModelManager, update the modelId of modelConfigInfo to the id of the ModelEntity
                log.info("from ModelManager find model: id={}, name={}, modelId={}, workspaceId={}", 
                    modelEntity.getId(), modelEntity.getName(), modelEntity.getModelId(), workspaceId);
                
                //Update modelId to the id of ModelEntity to ensure that the correct id is used later.
                modelConfigInfo.setModelId(modelEntity.getId());
            }
        } catch (Exception e) {
            log.error("Failed to parse model configuration: modelConfig={}", modelConfig, e);
            throw new RuntimeException("Model configuration parsing failed:" + e.getMessage(), e);
        }
        return modelConfigInfo;
    }
    
    /**
     * Extract model call parameters, obtain dynamic parameters from ModelConfigInfo and convert the format
     *
     * @param modelConfigInfo model configuration information object
     * @return Model call parameter Map
     */
    public Map<String, Object> extractModelParameters(ModelConfigInfo modelConfigInfo) {
        if (modelConfigInfo == null) {
            return new HashMap<>();
        }
        
        Map<String, Object> convertedParameters = new HashMap<>();
        Map<String, Object> originalParameters = modelConfigInfo.getAllParameters();
        
        //Conversion parameter names and formats
        for (Map.Entry<String, Object> entry : originalParameters.entrySet()) {
            String originalKey = entry.getKey();
            Object value = entry.getValue();
            
            if (value != null) {
                String convertedKey = convertParameterName(originalKey);
                convertedParameters.put(convertedKey, value);
            }
        }
        
        return convertedParameters;
    }
    
    /**
     * Convert parameter name format: Convert camel case naming to underscore naming to adapt to the OpenAI API format
     *
     * @param parameterName original parameter name
     * @return converted parameter name
     */
    private String convertParameterName(String parameterName) {
        //Automatically convert camelCase naming to underscore naming
        return parameterName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
    
    /**
     * Replace variables in Prompt template
     *
     * @param template Prompt template
     * @param variablesJson variable JSON string
     * @return Prompt after replacement
     */
    public String replaceVariables(String template, String variablesJson) {
        if (!StringUtils.hasText(template)) {
            return "";
        }
        
        if (!StringUtils.hasText(variablesJson)) {
            return template;
        }
        
        try {
            JsonNode variables = objectMapper.readTree(variablesJson);
            StringBuilder resultBuilder = new StringBuilder(template);
            
            //Replace all variable placeholders
            variables.fields().forEachRemaining(entry -> {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue().asText();
                String current = resultBuilder.toString();
                resultBuilder.setLength(0);
                resultBuilder.append(current.replace(placeholder, value));
            });
            
            return resultBuilder.toString();
        } catch (Exception e) {
            log.warn("Variable substitution failed, use original template: template={}, variables={}", template, variablesJson, e);
            return template;
        }
    }
    
    /**
     * Verify the validity of the model configuration. Only required fields are verified, and dynamic parameters are verified by the model service itself.
     *
     * @param modelConfigInfo model configuration information
     * @throws IllegalArgumentException if the configuration is invalid
     */
    public void validateModelConfig(ModelConfigInfo modelConfigInfo) {
        if (modelConfigInfo == null) {
            throw new IllegalArgumentException("Model configuration cannot be empty");
        }
        
        if (modelConfigInfo.getModelId() == null) {
            throw new IllegalArgumentException("Model ID cannot be empty");
        }
        
        //Only basic data type verification is performed, and the specific parameter range is verified by the model service.
        validateParameterTypes(modelConfigInfo);
    }
    
    /**
     * Verify whether the parameter type is reasonable and ensure that the numeric parameter is indeed a numeric type
     *
     * @param modelConfigInfo model configuration information
     */
    private void validateParameterTypes(ModelConfigInfo modelConfigInfo) {
        Map<String, Object> parameters = modelConfigInfo.getAllParameters();
        
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            
            if (value == null) {
                continue;
            }
            
            //For explicit numeric parameter names, verify the type
            if (isNumericParameterName(name)) {
                validateNumericValue(name, value);
            }
        }
    }
    
    /**
     * Determine whether the parameter name is a numeric type parameter
     *
     * @param parameterName parameter name
     * @return Is it a numeric parameter?
     */
    private boolean isNumericParameterName(String parameterName) {
        String lowerName = parameterName.toLowerCase();
        return lowerName.contains("temperature") || lowerName.contains("token") || lowerName.contains("top_")
                || lowerName.contains("penalty") || lowerName.contains("max_") || lowerName.contains("min_")
                || lowerName.endsWith("_p") || lowerName.endsWith("_k");
    }
    
    /**
     * Validate parameter values ​​of numeric types
     *
     * @param parameterName parameter name
     * @param value parameter value
     */
    private void validateNumericValue(String parameterName, Object value) {
        if (value instanceof Number) {
            return; //Already a numeric type
        }
        
        if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                return; //Can be converted to a numeric value
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        String.format("The value '%s' for parameter %s is not a valid number", parameterName, value));
            }
        }
        
        throw new IllegalArgumentException(String.format("The value '%s' of parameter %s should be of numeric type", parameterName, value));
    }
}
