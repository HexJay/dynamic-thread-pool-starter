package com.jovia.dynamic.threadpool.api;


import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;

/**
 * @author Jay
 * @date 2025-10-23-20:13
 */
public interface IConfigChangeListener {
    void onConfigChange(ThreadPoolConfig config);
}
