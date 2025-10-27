package com.jovia.dynamic.threadpool.registry.redis.config;

import com.alibaba.fastjson2.JSON;
import com.jovia.dynamic.threadpool.registry.redis.constant.RedisKeys;
import com.jovia.dynamic.threadpool.registry.redis.registry.RedisConfigCenter;
import com.jovia.dynamic.threadpool.registry.redis.trigger.job.ThreadPoolDataReportJob;
import com.jovia.dynamic.threadpool.registry.redis.trigger.listener.ThreadPoolConfigAdjustListener;
import com.jovia.dynamic.threadpool.api.IRegistry;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import org.apache.commons.lang3.StringUtils;
import org.redisson.Redisson;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.codec.JsonJacksonCodec;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Jay
 * @date 2025-10-20-23:16
 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(RedisAutoConfigProperties.class)
@ConditionalOnProperty(prefix = "dynamic.thread.pool", name = "config-center", havingValue = "redis")
@ConditionalOnProperty(prefix = "dynamic.thread.pool", name = "enabled", havingValue = "true")
@ConditionalOnBean(ThreadPoolExecutor.class) // 只有存在线程池时才生效
public class RedisConfig {

    private final Logger log = LoggerFactory.getLogger(RedisConfig.class);
    
    private final RedisAutoConfigProperties properties;

    private final ApplicationContext applicationContext;

    public RedisConfig(RedisAutoConfigProperties properties, ApplicationContext applicationContext) {
        this.properties = properties;
        this.applicationContext = applicationContext;
    }

    /**
     * 注入 Redisson
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonClient redisson() {
        // 单机模式
        Config config = new Config();
        config.setCodec(new JsonJacksonCodec())
                .useSingleServer()
                .setAddress("redis://" + properties.getHost() + ":" + properties.getPort())
                .setDatabase(properties.getDatabase());

        return Redisson.create(config);

    }

    @Bean
    public IRegistry redisRegistry(RedissonClient redisson) {
        return new RedisConfigCenter(redisson);
    }
    
    @Bean
    public ThreadPoolConfigAdjustListener threadPoolConfigAdjustListener(IDynamicThreadPoolService dynamicThreadPoolService) {
        return new ThreadPoolConfigAdjustListener(dynamicThreadPoolService);
    }
    
    @Bean
    public ThreadPoolDataReportJob threadPoolDataReportJob(IRegistry redisRegistry, IDynamicThreadPoolService dynamicThreadPoolService) {
        return new ThreadPoolDataReportJob(redisRegistry, dynamicThreadPoolService);
    }
    
    @Bean
    public RTopic rTopic(RedissonClient redisson, ThreadPoolConfigAdjustListener threadPoolConfigAdjustListener) {
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        RTopic topic = redisson.getTopic(RedisKeys.THREAD_POOL_CONFIG_TOPIC + appName);
        topic.addListener(String.class, threadPoolConfigAdjustListener);
        return topic;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initializeThreadPool(RedissonClient redisson) {
        String appName = applicationContext.getEnvironment().getProperty("spring.application.name");
        // 动态获取 ThreadPoolExecutor Map
        Map<String, ThreadPoolExecutor> threadPoolExecutorMap =
                applicationContext.getBeansOfType(ThreadPoolExecutor.class);
        // 启动时，从配置中心获取配置
        RMap<String, String> appMap = redisson.getMap(RedisKeys.THREAD_POOL_CONFIGS + appName);

        Set<String> threadPoolKeys = threadPoolExecutorMap.keySet();
        for (String threadPoolKey : threadPoolKeys) {
            String json = appMap.get(threadPoolKey);
            if (StringUtils.isBlank(json)) {
                log.warn("线程池 [{}] 未在配置中心找到配置，使用默认参数", threadPoolKey);
                continue;
            }

            ThreadPoolConfig threadPoolConfig = JSON.parseObject(json, ThreadPoolConfig.class);
            if (threadPoolConfig == null) continue;
            if (threadPoolConfig.getCorePoolSize() > threadPoolConfig.getMaximumPoolSize()) {
                log.warn("[DynamicThreadPool] 配置错误：corePoolSize > maximumPoolSize for {}", threadPoolKey);
                continue;
            }

            log.info("初始配置:{}", JSON.toJSONString(threadPoolConfig));
            ThreadPoolExecutor threadPoolExecutor = threadPoolExecutorMap.get(threadPoolKey);
            threadPoolExecutor.setMaximumPoolSize(threadPoolConfig.getMaximumPoolSize());
            threadPoolExecutor.setCorePoolSize(threadPoolConfig.getCorePoolSize());
        }
    }
}
