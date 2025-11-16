package com.jovia.dynamic.threadpool.core.model.entity;

import lombok.Data;

/**
 * 系统监控指标，用于线程池动态调整
 * 
 * @author Jay
 * @date 2025-10-27-15:56
 */
@Data
public class SystemMetrics {
    /** CPU 总体使用率（0~1） */
    private double cpuUsage;
    /** 内存使用率（0~1） */
    private double memoryUsedPercent;
}
