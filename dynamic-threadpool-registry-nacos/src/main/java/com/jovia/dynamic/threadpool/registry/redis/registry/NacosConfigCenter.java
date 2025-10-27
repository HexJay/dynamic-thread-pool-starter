package com.jovia.dynamic.threadpool.registry.redis.registry;

import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.jovia.dynamic.threadpool.api.IConfigCenter;
import com.jovia.dynamic.threadpool.api.IConfigChangeListener;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @author Jay
 * @date 2025-10-23-20:16
 */
@Slf4j
public class NacosConfigCenter implements IConfigCenter {

    private final ConfigService configService;

    public NacosConfigCenter(ConfigService configService) {
        this.configService = configService;
    }

    @Override
    public List<ThreadPoolConfig> fetchThreadPoolConfigs(String appName) {
        String dataId = "thread-pool-config-" + appName;
        try {
            String content = configService.getConfig(dataId, "DEFAULT_GROUP", 3000);
            return JSON.parseArray(content, ThreadPoolConfig.class);
        } catch (NacosException e) {
            log.error("[NacosConfigCenter] fetch thread pool configs error", e);
            return Collections.emptyList();
        }
    }

    @Override
    public void subscribeConfigChange(String appName, IConfigChangeListener listener) {
        String dataId = "thread-pool-config-" + appName;
        try {
            configService.addListener(dataId, "DEFAULT_GROUP", new Listener() {
                @Override
                public Executor getExecutor() {
                    return null;
                }

                @Override
                public void receiveConfigInfo(String s) {
                    log.info("[NacosConfigCenter] subscribe config changed to " + s);
                    List<ThreadPoolConfig> threadPoolConfigEntities = JSON.parseArray(s, ThreadPoolConfig.class);
                    threadPoolConfigEntities.forEach(listener::onConfigChange);
                }
            });
        } catch (Exception e) {
            log.error("[NacosConfigCenter] subscribe config change error", e);
            throw new RuntimeException(e);
        }
    }
}
