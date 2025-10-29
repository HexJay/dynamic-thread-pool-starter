package com.jovia.dynamic.threadpool.core.model.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统监控指标，用于线程池动态调整
 * @author Jay
 * @date 2025-10-27-15:56
 */
@Data
public class SystemMetrics {
    /** CPU 总体使用率（0~1） */
    private double cpuUsage;
    /** CPU 用户态使用率（0~1） */
    private double cpuUserUsage;
    /** CPU 系统态使用率（0~1） */
    private double cpuSystemUsage;
    /** CPU IO 等待比例（0~1） */
    private double cpuIoWaitUsage;
    /** CPU 空闲率（0~1） */
    private double cpuIdleUsage;
    /** CPU 逻辑核心数 */
    private int cpuLogicalCount;
    /** 系统平均负载 */
    private double systemLoadAverage;
    /** 系统总内存（字节） */
    private long memoryTotal;
    /** 可用内存（字节） */
    private long memoryAvailable;
    /** 内存使用率（0~1） */
    private double memoryUsedPercent;
    /** 采样时间 */
    private LocalDateTime timestamp;
}
