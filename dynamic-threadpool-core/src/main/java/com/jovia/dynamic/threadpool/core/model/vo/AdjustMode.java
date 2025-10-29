package com.jovia.dynamic.threadpool.core.model.vo;

/**
 * @author Jay
 * @date 2025-10-29-20:12
 */
public enum AdjustMode {

    AUTO("auto",0),
    MANUAL("manual",1);

    public final String desc;
    public final int code;

    AdjustMode(String desc, int  code) {
        this.desc = desc;
        this.code = code;
    }
}
