package com.jovia.dynamic.threadpool.core.config;

import com.alibaba.fastjson.JSON;
import com.jovia.dynamic.threadpool.core.domain.pool.AdaptiveThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.service.DynamicThreadPoolService;
import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Jay
 * @date 2025/10/19 00:24
 * @description 动态配置入口
 */
@AutoConfiguration
@EnableScheduling
@ConditionalOnClass(ThreadPoolExecutor.class)
@EnableConfigurationProperties(AdaptiveThreadPoolProperties.class)
@ConditionalOnProperty(prefix = "adaptive.thread.pool", name = "enabled", havingValue = "true")
public class AdaptiveThreadPoolAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveThreadPoolAutoConfig.class);

    private final ApplicationContext applicationContext;

    public AdaptiveThreadPoolAutoConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        SystemMetricsMonitor.start(1000);
    }

    @Bean
    @ConditionalOnBean(ThreadPoolExecutor.class)
    public IDynamicThreadPoolService dynamicThreadPoolService(Map<String, AdaptiveThreadPoolExecutor> threadPoolExecutorMap) {
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        if (StringUtils.isBlank(appName)) {
            throw new IllegalStateException("[DynamicThreadPool] 启动失败：未配置 spring.application.name，请在 application.yml 中配置。");
        }
        log.info("线程池信息:{}", JSON.toJSONString(threadPoolExecutorMap.keySet()));
        return new DynamicThreadPoolService(appName, threadPoolExecutorMap);
    }
    
}
