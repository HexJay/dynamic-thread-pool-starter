package com.jovia.dynamic.threadpool.registry.redis.trigger.job;


import com.alibaba.fastjson2.JSON;
import com.jovia.dynamic.threadpool.api.IRegistry;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.model.aggregate.ThreadPoolStatusAggregate;
import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 线程池数据上报任务
 * @author Jay
 * @date 2025-10-19-16:47
 */
public class ThreadPoolDataReportJob {
    
    private final Logger logger = LoggerFactory.getLogger(ThreadPoolDataReportJob.class);
    private IRegistry registry;
    private IDynamicThreadPoolService dynamicThreadPoolService;

    public ThreadPoolDataReportJob(IRegistry registry, IDynamicThreadPoolService dynamicThreadPoolService) {
        this.registry = registry;
        this.dynamicThreadPoolService = dynamicThreadPoolService;
    }

    // 保存上一次上报的配置，用于检测变化
    private final Map<String, ThreadPoolStatusAggregate> lastReported = new ConcurrentHashMap<>();
    
    @Scheduled(cron = "*/5 * * * * ?")
    public void report() {

        List<ThreadPoolStatusAggregate> currentList  = dynamicThreadPoolService.queryAllThreadPools();

        for (ThreadPoolStatusAggregate current : currentList) {
            
            ThreadPoolConfig config = current.getThreadPoolConfig();
            ThreadPoolMetrics metrics = current.getThreadPoolMetrics();
            
            ThreadPoolStatusAggregate last = lastReported.get(config.getThreadPoolName());
            if (last == null || !Objects.equals(current, last)) {
                registry.reportThreadPoolConfig(config);
                lastReported.put(config.getThreadPoolName(), current);
                logger.info("检测到线程池配置变更，上报: {}", JSON.toJSONString(current));
            }
        }
    }
}
