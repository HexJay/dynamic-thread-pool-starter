package com.jovia.dynamic.threadpool.core.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Jay
 * @date 2025-10-25-17:05
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolMetrics {

    private String appName;
    private String poolName;
    private int corePoolSize;
    private int maximumPoolSize;
    private int activeCount;
    private int poolSize;
    private int queueSize;
    private int remainingCapacity;
    /**
     * 历史最大线程数
     */
    private int largestPoolSize;
    /**
     * 已完成任务数
     */
    private long completedTaskCount;
    /**
     * EWMA 平滑后的平均任务执行时间（ms）
     */
    private double ewmaTaskTime;
    /** EWMA平滑后的平均任务等待时间 (ms) */
    private double ewmaQueueWait;
}
