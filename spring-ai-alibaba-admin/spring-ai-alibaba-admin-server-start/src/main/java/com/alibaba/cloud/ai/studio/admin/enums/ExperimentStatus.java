package com.alibaba.cloud.ai.studio.admin.enums;

public enum ExperimentStatus {

    /**
     * draft
     */
    DRAFT("DRAFT", "draft"),

    /**
     * Running
     */
    RUNNING("RUNNING", "Running"),

    /**
     * Completed
     */
    COMPLETED("COMPLETED", "Completed"),

    /**
     * fail
     */
    FAILED("FAILED", "fail"),

    /**
     * Stopped
     */
    STOPPED("STOPPED", "Stopped");

    private final String code;
    private final String description;

    ExperimentStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ExperimentStatus fromCode(String code) {
        for (ExperimentStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown experiment status code: " + code);
    }
} 
