package com.alibaba.cloud.ai.studio.admin.exception;

import com.alibaba.nacos.api.common.Constants;
import com.alibaba.nacos.api.utils.StringUtils;

public class StudioException extends Exception {
    
    /**
     * Invalid param (parameter error).
     */
    public static final int INVALID_PARAM = 400;
    
    /**
     * No right (authorization failed).
     */
    public static final int NO_RIGHT = 403;
    
    /**
     * not found.
     */
    public static final int NOT_FOUND = 404;
    
    /**
     * Conflict (concurrent write conflict).
     */
    public static final int CONFLICT = 409;
    
    /**
     * Server error (e.g., timeout).
     */
    public static final int SERVER_ERROR = 500;
    
    /**
     * Bad gateway (routing error, e.g., backend server down).
     */
    public static final int BAD_GATEWAY = 502;
    
    /**
     * Over threshold (exceeds server-side rate limit).
     */
    public static final int OVER_THRESHOLD = 503;
    
    private int errCode;
    
    private String errMsg;
    
    private Throwable causeThrowable;
    
    public StudioException() {
    }
    
    public StudioException(final int errCode, final String errMsg) {
        super(errMsg);
        this.errCode = errCode;
        this.errMsg = errMsg;
    }
    
    public StudioException(final int errCode, final Throwable throwable) {
        super(throwable);
        this.errCode = errCode;
        this.setCauseThrowable(throwable);
    }
    
    public StudioException(final int errCode, final String errMsg, final Throwable throwable) {
        super(errMsg, throwable);
        this.errCode = errCode;
        this.errMsg = errMsg;
        this.setCauseThrowable(throwable);
    }
    
    public int getErrCode() {
        return this.errCode;
    }
    
    public void setErrCode(final int errCode) {
        this.errCode = errCode;
    }
    
    public String getErrMsg() {
        if (!StringUtils.isBlank(this.errMsg)) {
            return this.errMsg;
        }
        if (this.causeThrowable != null) {
            return this.causeThrowable.getMessage();
        }
        return Constants.NULL;
    }
    
    public void setErrMsg(final String errMsg) {
        this.errMsg = errMsg;
    }
    
    public void setCauseThrowable(final Throwable throwable) {
        this.causeThrowable = this.getCauseThrowable(throwable);
    }
    
    private Throwable getCauseThrowable(final Throwable t) {
        if (t.getCause() == null) {
            return t;
        }
        return this.getCauseThrowable(t.getCause());
    }
    
    @Override
    public String toString() {
        return "ErrCode:" + getErrCode() + ", ErrMsg:" + getErrMsg();
    }
    
    
}
