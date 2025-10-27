package com.jovia.dynamic.threadpool.core.service;


import com.alibaba.fastjson2.JSON;
import com.jovia.dynamic.threadpool.core.MonitoringThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.aggregate.ThreadPoolContext;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsCollector;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;


/**
 * @author Jay
 * @date 2025-10-19-15:17
 */
@Slf4j
public class DynamicThreadPoolService implements IDynamicThreadPoolService {

    private final Logger logger = LoggerFactory.getLogger(DynamicThreadPoolService.class);
    private final Map<String, ThreadPoolContext> threadPoolContextMap;
    private final String appName;

    public DynamicThreadPoolService(String appName, Map<String, MonitoringThreadPoolExecutor> threadPoolMap) {
        this.appName = appName;
        this.threadPoolContextMap = buildContext(threadPoolMap);
    }

    private Map<String, ThreadPoolContext> buildContext(Map<String, MonitoringThreadPoolExecutor> threadPoolMap) {
        Map<String, ThreadPoolContext> threadPoolContextMap = new ConcurrentHashMap<>();
        Set<Map.Entry<String, MonitoringThreadPoolExecutor>> entries = threadPoolMap.entrySet();

        for (Map.Entry<String, MonitoringThreadPoolExecutor> entry : entries) {
            String threadPoolName = entry.getKey();
            MonitoringThreadPoolExecutor threadPoolExecutor = entry.getValue();

            if (threadPoolExecutor == null) {
                log.info("threadPool {} 不存在.", threadPoolName);
                return null;
            }

            ThreadPoolConfig config = new ThreadPoolConfig();
            config.setThreadPoolName(threadPoolName);
            config.setCorePoolSize(threadPoolExecutor.getCorePoolSize());
            config.setMaximumPoolSize(threadPoolExecutor.getMaximumPoolSize());
            config.setKeepAliveTime(threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS));
            config.setAllowCoreThreadTimeOut(threadPoolExecutor.allowsCoreThreadTimeOut());
            config.setQueueType(threadPoolExecutor.getQueue().getClass().getSimpleName());
            config.setHandler(threadPoolExecutor.getRejectedExecutionHandler());
            config.setAdjustMode(ThreadPoolConfig.Mode.MANUAL.code);
            config.setLastUpdateTime(System.currentTimeMillis());

            ThreadPoolMetrics metrics = collectMetrics(threadPoolName);

            ThreadPoolContext threadPoolContext = new ThreadPoolContext(appName, threadPoolName, threadPoolExecutor, config, metrics);
            threadPoolContextMap.put(threadPoolName, threadPoolContext);
        }

        return threadPoolContextMap;
    }

    @Override
    public List<ThreadPoolContext> queryAllThreadPools() {
        return threadPoolContextMap.values().stream().toList();
    }

    @Override
    public ThreadPoolContext queryThreadPoolByName(String threadPoolName) {
        ThreadPoolContext threadPoolContext = threadPoolContextMap.get(threadPoolName);

        if (threadPoolContext == null) {
            return null;
        }

        if (logger.isDebugEnabled()) {
            logger.info("动态线程池，配置查询 应用名:{} 线程名:{} 池化配置:{}", appName, threadPoolName, JSON.toJSONString(threadPoolContext.getThreadPoolConfig()));
        }

        return threadPoolContext;
    }

    @Override
    public void updateThreadPoolConfig(ThreadPoolConfig config) {

        ThreadPoolContext threadPoolContext = threadPoolContextMap.get(config.getThreadPoolName());
        MonitoringThreadPoolExecutor threadPoolExecutor = threadPoolContext.getThreadPoolExecutor();

        if (threadPoolExecutor == null) {
            logger.warn("[动态线程池] 未找到线程池: {}", config.getThreadPoolName());
            return;
        }

        int coreSize = config.getCorePoolSize();
        int maxSize = config.getMaximumPoolSize();

        if (coreSize <= 0 || maxSize <= 0 || coreSize > maxSize) {
            logger.warn("[动态线程池] 配置不合法: corePoolSize={}, maxPoolSize={}, 跳过更新", coreSize, maxSize);
            return;
        }

        // 设置核心线程数和最大线程数,先更新最大线程数
        threadPoolExecutor.setCorePoolSize(coreSize);
        threadPoolExecutor.setMaximumPoolSize(maxSize);
        threadPoolExecutor.setRejectedExecutionHandler(config.getHandler());
    }

    @Override
    public ThreadPoolMetrics collectMetrics(String poolName) {

        ThreadPoolContext threadPoolContext = threadPoolContextMap.get(poolName);
        if (threadPoolContext == null) {
            return null;
        }
        MonitoringThreadPoolExecutor threadPoolExecutor = threadPoolContext.getThreadPoolExecutor();

        return ThreadPoolMetrics.builder()
                .corePoolSize(threadPoolExecutor.getCorePoolSize())
                .maximumPoolSize(threadPoolExecutor.getMaximumPoolSize())
                .activeCount(threadPoolExecutor.getActiveCount())
                .poolSize(threadPoolExecutor.getPoolSize())
                .queueSize(threadPoolExecutor.getQueue().size())
                .remainingCapacity(threadPoolExecutor.getQueue().remainingCapacity())
                .largestPoolSize(threadPoolExecutor.getLargestPoolSize())
                .completedTaskCount(threadPoolExecutor.getCompletedTaskCount())
                .avgTaskTimeMillis(threadPoolExecutor.getAvgTaskTimeMillis())
                .build();
    }

    @Override
    public void adjustIfNeeded() {
        Set<String> threadPoolNames = threadPoolContextMap.keySet();
        for (String poolName : threadPoolNames) {
            ThreadPoolContext context = threadPoolContextMap.get(poolName);
            ThreadPoolConfig config = context.getThreadPoolConfig();
            
            if (config == null || config.getAdjustMode() != ThreadPoolConfig.Mode.AUTO.code) {
                continue;
            }
            
            
        }
    }
}
