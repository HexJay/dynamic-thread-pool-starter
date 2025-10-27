package com.jovia.dynamic.threadpool.core.config;

import com.alibaba.fastjson.JSON;
import com.jovia.dynamic.threadpool.core.MonitoringThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.service.DynamicThreadPoolService;
import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import com.jovia.dynamic.threadpool.core.trigger.job.AdaptiveAdjusterJob;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

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
@EnableConfigurationProperties(DynamicThreadPoolAutoConfigProperties.class)
@ConditionalOnProperty(prefix = "dynamic.thread.pool", name = "enabled", havingValue = "true")
public class DynamicThreadPoolAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicThreadPoolAutoConfig.class);

    private final ApplicationContext applicationContext;

    public DynamicThreadPoolAutoConfig(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Bean
    @ConditionalOnBean(ThreadPoolExecutor.class)
    public IDynamicThreadPoolService dynamicThreadPoolService(Map<String, MonitoringThreadPoolExecutor> threadPoolExecutorMap) {
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        if (StringUtils.isBlank(appName)) {
            throw new IllegalStateException("[DynamicThreadPool] 启动失败：未配置 spring.application.name，请在 application.yml 中配置。");
        }
        log.info("线程池信息:{}", JSON.toJSONString(threadPoolExecutorMap.keySet()));
        return new DynamicThreadPoolService(appName, threadPoolExecutorMap);
    }
    
    @Bean
    public AdaptiveAdjusterJob adaptiveAdjusterJob(IDynamicThreadPoolService dynamicThreadPoolService) {
        return new AdaptiveAdjusterJob(dynamicThreadPoolService);
    }
}
