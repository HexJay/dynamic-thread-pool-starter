package com.jovia.dynamic.threadpool.core.model.entity;

import lombok.*;

/**
 * 线程池配置实体对象
 *
 * @author Jay
 * @date 2025-10-19-15:04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {
        "threadPoolName"
})
public class ThreadPoolConfig {

    public ThreadPoolConfig(String threadPoolName) {
        this.threadPoolName = threadPoolName;
    }
    
    /**
     * 线程池名称
     */
    private String threadPoolName;

    /**
     * 核心线程数
     */
    private int corePoolSize;

    /**
     * 最大线程数
     */
    private int maximumPoolSize;

    /**
     * 空闲线程过期时间
     */
    private long keepAliveTime;

    /**
     * 允许核心线程过期
     */
    private boolean allowCoreThreadTimeOut = false;

    /**
     * 队列类型
     */
    private String queueType;

    /**
     * 拒绝策略
     */
    private String handler;

    // 新增：自适应模式
    private String adjustMode; // AUTO 0 / MANUAL 1 

    private long lastUpdateTime;
    
}
