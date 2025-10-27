package com.jovia.dynamic.threadpool.api;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;

import java.util.List;

/**
 * 注册中心接口
 * @author Jay
 * @date 2025-10-23-21:06
 */
public interface IConfigCenter {

    /**
     * 初始化并从配置中心拉取配置
     */
    List<ThreadPoolConfig> fetchThreadPoolConfigs(String appName);

    /**
     * 订阅配置变更
     */
    void subscribeConfigChange(String appName, IConfigChangeListener listener);
}
