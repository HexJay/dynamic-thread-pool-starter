package com.jovia.dynamic.threadpool.core.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Jay
 * @date 2025-10-29-20:24
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AutoAdjustConfig {
    // 调整间隔
    @Builder.Default
    private long adjustIntervalMs = 10000; // 默认10秒

    // 扩容阈值
    @Builder.Default
    private double queueWaitThresholdMs = 1000; // 队列等待时间阈值
    @Builder.Default
    private double queueFullThreshold = 0.8; // 队列满载率阈值
    @Builder.Default
    private double queueWaitLowMs = 20; // 队列等待时间期望最低值
    @Builder.Default
    private double queueWaitWeight = 0.5;

    // 缩容阈值
    @Builder.Default
    private long idleShrinkThresholdMs = 30000; // 空闲缩容阈值(30秒)

    // 系统资源限制
    @Builder.Default
    private double maxCpuUsage = 0.8; // 最大CPU使用率
    @Builder.Default
    private double maxMemoryUsage = 0.8; // 最大内存使用率

    // 调整步长
    @Builder.Default
    private int corePoolStep = 1; // 核心线程数步长
    @Builder.Default
    private int maxPoolStep = 2; // 最大线程数步长
    @Builder.Default
    private int queueStep = 10; // 队列容量步长

    // 参数上限
    @Builder.Default
    private int maxCorePoolSize = 50; // 最大核心线程数
    @Builder.Default
    private int maxMaximumPoolSize = 100; // 最大最大线程数
    @Builder.Default
    private int maxQueueCapacity = 1000; // 最大队列容量

    // 功能开关
    @Builder.Default
    private boolean useSystemMetrics = true; // 是否使用系统指标
    @Builder.Default
    private boolean allowShrink = true; // 是否允许缩容
}
