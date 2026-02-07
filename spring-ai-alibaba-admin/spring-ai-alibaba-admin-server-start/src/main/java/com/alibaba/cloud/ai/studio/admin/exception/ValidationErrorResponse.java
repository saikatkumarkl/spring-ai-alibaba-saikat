package com.alibaba.cloud.ai.studio.admin.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationErrorResponse {

    /**
     * Error message.
     */
    private String message;

    /**
     * Field error details list.
     */
    private List<FieldError> fieldErrors;

    /**
     * Field error details.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldError {
        
        /**
         * Field name with error.
         */
        private String field;
        
        /**
         * Rejected value.
         */
        private Object rejectedValue;
        
        /**
         * Error message.
         */
        private String message;
    }
}
