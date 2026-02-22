package com.alibaba.cloud.ai.studio.admin.enums;

public enum versionStatus {

    /**
     * draft
     */
    DRAFT("DRAFT", "draft"),

    /**
     * Published
     */
    PUBLISHED("PUBLISHED", "Published");



    private final String code;
    private final String description;

    versionStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static versionStatus fromCode(String code) {
        for (versionStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown dataset status code: " + code);
    }
} 
