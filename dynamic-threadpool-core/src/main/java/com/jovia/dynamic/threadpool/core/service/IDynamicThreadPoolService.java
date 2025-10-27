package com.jovia.dynamic.threadpool.core.service;

import com.jovia.dynamic.threadpool.core.model.aggregate.ThreadPoolContext;
import com.jovia.dynamic.threadpool.core.model.aggregate.ThreadPoolStatusAggregate;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;

import java.util.List;

/**
 * @author Jay
 * @date 2025-10-19-15:03
 */
public interface IDynamicThreadPoolService {

    List<ThreadPoolContext> queryAllThreadPools();
    
    ThreadPoolContext queryThreadPoolByName(String threadPoolName);
    
    void updateThreadPoolConfig(ThreadPoolConfig threadPoolConfig);

    ThreadPoolMetrics collectMetrics(String poolName);
    
    void adjustIfNeeded();
}
