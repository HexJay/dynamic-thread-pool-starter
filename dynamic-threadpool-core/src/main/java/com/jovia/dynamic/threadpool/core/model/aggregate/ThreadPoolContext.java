package com.jovia.dynamic.threadpool.core.model.aggregate;

import com.alibaba.fastjson2.annotation.JSONField;
import com.jovia.dynamic.threadpool.core.MonitoringThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Jay
 * @date 2025-10-27-19:57
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ThreadPoolContext {
    private String appName;
    private String threadPoolName;
    
    @JSONField(serialize = false)
    private MonitoringThreadPoolExecutor threadPoolExecutor;
    
    private ThreadPoolConfig threadPoolConfig;
    private ThreadPoolMetrics threadPoolMetrics;
}
