package com.jovia.dynamic.threadpool.core.service;


import com.alibaba.fastjson2.JSON;
import com.jovia.dynamic.threadpool.core.domain.pool.DynamicThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.aggregate.ThreadPoolContext;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @author Jay
 * @date 2025-10-19-15:17
 */
@Slf4j
public class DynamicThreadPoolService implements IDynamicThreadPoolService {

    private final Logger logger = LoggerFactory.getLogger(DynamicThreadPoolService.class);
    private final Map<String, ThreadPoolContext> threadPoolContextMap;
    private final String appName;

    public DynamicThreadPoolService(String appName, Map<String, DynamicThreadPoolExecutor> threadPoolMap) {
        this.appName = appName;
        this.threadPoolContextMap = buildContext(threadPoolMap);
    }

    private Map<String, ThreadPoolContext> buildContext(Map<String, DynamicThreadPoolExecutor> threadPoolMap) {
        Map<String, ThreadPoolContext> threadPoolContextMap = new ConcurrentHashMap<>();
        Set<Map.Entry<String, DynamicThreadPoolExecutor>> entries = threadPoolMap.entrySet();

        for (Map.Entry<String, DynamicThreadPoolExecutor> entry : entries) {
            String threadPoolName = entry.getKey();
            DynamicThreadPoolExecutor threadPoolExecutor = entry.getValue();

            if (threadPoolExecutor == null) {
                log.info("threadPool {} 不存在.", threadPoolName);
                return null;
            }

            ThreadPoolConfig config = threadPoolExecutor.getThreadPoolConfig();
            config.setThreadPoolName(threadPoolName);
            
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
        DynamicThreadPoolExecutor threadPoolExecutor = threadPoolContext.getThreadPoolExecutor();

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
    }

    @Override
    public ThreadPoolMetrics collectMetrics(String poolName) {

        ThreadPoolContext threadPoolContext = threadPoolContextMap.get(poolName);
        if (threadPoolContext == null) {
            return null;
        }
        DynamicThreadPoolExecutor threadPoolExecutor = threadPoolContext.getThreadPoolExecutor();

        ThreadPoolMetrics threadPoolMetrics = threadPoolExecutor.getThreadPoolMetrics();
        threadPoolMetrics.setPoolName(poolName);
        threadPoolMetrics.setAppName(appName);
        
        return threadPoolMetrics;
    }

    @Override
    public void adjustIfNeeded() {
        Set<String> threadPoolNames = threadPoolContextMap.keySet();
        for (String poolName : threadPoolNames) {
            ThreadPoolContext context = threadPoolContextMap.get(poolName);
            ThreadPoolConfig config = context.getThreadPoolConfig();
            
            if (config == null || !Objects.equals(config.getAdjustMode(), ThreadPoolConfig.Mode.AUTO.desc)) {
                continue;
            }
            
            
        }
    }
    
    
}
