package com.jovia.dynamic.threadpool.core.domain.pool;

import com.jovia.dynamic.threadpool.core.model.entity.AdjustmentDecision;
import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.model.vo.AdjustMode;
import com.jovia.dynamic.threadpool.core.model.vo.AutoAdjustConfig;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsCollector;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Jay
 * @date 2025-10-27-16:44
 */
@Slf4j
public class DynamicThreadPoolExecutor extends ThreadPoolExecutor {

    private final MetricsTrackingRejectedExecutionHandler trackingHandler;

    private final ReentrantLock adjustLock = new ReentrantLock();
    
    private final int slowStartMaxMultiplier = 8; // 慢启动的最大倍数
    private volatile int currentGrowthFactor = 1; // 慢启动增长因子（1,2,4,...）
    private volatile int consecutiveOverloadsCounter = 0; // 连续拥塞检测次数
    // 滞后阈值（扩容与收缩用不同阈值，避免来回抖动）
    private final double queueFullExpandThreshold = 0.8;
    private final double queueFullShrinkThreshold = 0.4;
    
    // 初始参数记录
    private final int initialCorePoolSize;
    private final int initialMaxPoolSize;
    private final int initialQueueCapacity;

    // 新增调整控制字段
    private volatile long lastAdjustTime = 0;
    
    private volatile int consecutiveOverloads = 0; // 连续过载次数

    private volatile int consecutiveIdleChecks = 0;
    private final int idleChecksThreshold = 3; // 需要连续 N 次空闲才缩容

    
    @Getter
    private volatile double ewmaTaskTime = 0.0; // 平滑任务执行时间
    @Getter
    private volatile double ewmaQueueWait = 0.0; // 平滑队列任务等待时间

    private static final double ALPHA = 0.3; // EWMA 平滑系数（0~1之间）

    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Getter
    private long lastUpdateTime = System.currentTimeMillis();
    @Getter
    private volatile AdjustMode adjustMode = AdjustMode.MANUAL;

    // 历史繁忙时间跟踪
    private volatile long lastBusyTime = System.currentTimeMillis();

    // 调整配置
    private volatile AutoAdjustConfig autoAdjustConfig = new AutoAdjustConfig();
    
    public DynamicThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit, int capacity,
                                     ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new ResizableBlockingQueue<>(capacity), threadFactory,
                new MetricsTrackingRejectedExecutionHandler(handler));
        this.trackingHandler = (MetricsTrackingRejectedExecutionHandler) getRejectedExecutionHandler();
        this.initialCorePoolSize = corePoolSize;
        this.initialMaxPoolSize = maximumPoolSize;
        this.initialQueueCapacity = capacity;
    }
    
    @Override
    public void execute(Runnable command) {
        long now = System.nanoTime();
        lastBusyTime = System.currentTimeMillis(); // 更新繁忙时间
        super.execute(new TimedRunnable(command, now));
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        if (r instanceof TimedRunnable tr) {
            long waitTimeNanos = System.nanoTime() - tr.submitTime;
            double waitTimeMillis = waitTimeNanos / 1_000_000.0;
            ewmaQueueWait = ALPHA * waitTimeMillis + (1 - ALPHA) * ewmaQueueWait;
        }
        startTime.set(System.nanoTime());
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        long endTime = System.nanoTime();
        long durationNs = endTime - startTime.get();
        double durationMs = durationNs / 1_000_000.0;
        // 指数加权移动平均 (EWMA)
        ewmaTaskTime = ALPHA * durationMs + (1 - ALPHA) * ewmaTaskTime;
        startTime.remove();

        // 在任务执行后检查是否需要调整（仅在自动模式下）
        if (adjustMode == AdjustMode.AUTO) {
            adjustmentIfNeeded();
        }
    }

    private void adjustmentIfNeeded() {
        long now = System.currentTimeMillis();
        // 冷却期检查，避免频繁调整
        if (now - lastAdjustTime < autoAdjustConfig.getAdjustIntervalMs()) {
            return;
        }

        if (!adjustLock.tryLock()) {
            return; // 另一个线程正在调整
        }

        try {
            // double-check 冷却
            if (System.currentTimeMillis() - lastAdjustTime < autoAdjustConfig.getAdjustIntervalMs()) {
                return;
            }
            
            ThreadPoolMetrics poolMetrics = getThreadPoolMetrics();
            // 收集系统指标（异步或按需）
            SystemMetrics systemMetrics = null;
            if (autoAdjustConfig.isUseSystemMetrics()) {
                systemMetrics = SystemMetricsMonitor.getLastSystemMetrics();
            }
            
            if (systemMetrics == null) {
                return;
            }

            AdjustmentDecision decision = createAdjustmentDecision(poolMetrics, systemMetrics);

            if (decision.shouldAdjust()) {
                applyAdjustment(decision);
                lastAdjustTime = now;
                log.info("线程池自动调整: {}", decision.getReason());
            }

        } catch (Exception e) {
            log.error("线程池参数调整失败", e);
        }finally {
            adjustLock.unlock();
        }
    }
    
    private AdjustmentDecision createAdjustmentDecision(ThreadPoolMetrics poolMetrics,
                                                        SystemMetrics systemMetrics) {
        // 1. 检查是否需要扩容（高负载情况）
        AdjustmentDecision expansionDecision = checkForExpansion(poolMetrics, systemMetrics);
        if (expansionDecision.shouldAdjust()) {
            return expansionDecision;
        }

        // 2. 检查是否需要缩容（空闲情况）
        AdjustmentDecision shrinkDecision = checkForShrink(poolMetrics, systemMetrics);
        if (shrinkDecision.shouldAdjust()) {
            return shrinkDecision;
        }

        return AdjustmentDecision.NO_CHANGE;
    }
    
    /**
     * 扩容检查
     */
    private AdjustmentDecision checkForExpansion(ThreadPoolMetrics poolMetrics,
                                                 SystemMetrics systemMetrics) {
        
        if (systemMetrics != null && !isSystemHealthyForExpansion(systemMetrics)) {
            // 系统太忙，认为是“拥塞”，但是不能扩容 —— 重置或增加计数取决实现
            consecutiveOverloadsCounter = 0; // 或保持不变
            return AdjustmentDecision.noChange("系统负载过高");
        }

        boolean queueNearlyFull = isQueueNearlyFull(poolMetrics); // 使用 autoAdjustConfig.getQueueFullThreshold()
        boolean waitTooLong = ewmaQueueWait > autoAdjustConfig.getQueueWaitThresholdMs();

        if (queueNearlyFull || waitTooLong) {
            consecutiveOverloadsCounter++;
            // 慢启动：根据连续次数计算增长因子
            currentGrowthFactor = Math.min(slowStartMaxMultiplier, 1 << Math.max(0, consecutiveOverloadsCounter - 1));
            // 决策：若活跃线程接近上限 -> 尝试提升 max，否则提升 core
            if (poolMetrics.getActiveCount() >= poolMetrics.getMaximumPoolSize() * 0.9) {
                return createExpansionDecision(poolMetrics,"高负载且连续 " + consecutiveOverloadsCounter + " 次", AdjustmentDecision.Type.EXPAND_MAX);
            } else {
                return createExpansionDecision(poolMetrics,"高负载且连续 " + consecutiveOverloadsCounter + " 次", AdjustmentDecision.Type.EXPAND_CORE);
            }
        } else {
            // 非拥塞，重置慢启动计数（进入拥塞避免/稳定阶段）
            consecutiveOverloadsCounter = 0;
            currentGrowthFactor = 1;
        }
        return AdjustmentDecision.noChange("无需扩容");
    }

    public AdjustmentDecision checkForShrink(ThreadPoolMetrics pool, SystemMetrics sys) {
        // --- Step 1: 系统空闲判断 ---
        if (sys != null && sys.getCpuUsage() < 0.3 && pool.getActiveCount() < pool.getCorePoolSize() * 0.3) {
            return createShrinkDecision(pool, "系统空闲，考虑缩减核心线程数");
        }

        // --- Step 2: 队列空闲判断 ---
        if (pool.getQueueSize() == 0 && pool.getActiveCount() < pool.getCorePoolSize() / 2) {
            consecutiveIdleChecks++;
            if (consecutiveIdleChecks >= idleChecksThreshold) {
                consecutiveIdleChecks = 0;
                return createShrinkDecision(pool, "连续空闲触发");
            }
        } else {
            consecutiveIdleChecks = 0;
        }

        return AdjustmentDecision.noChange("无需缩容");
    }
    
    /** 创建扩容决策 */
    private AdjustmentDecision createExpansionDecision(ThreadPoolMetrics metrics, String reason, AdjustmentDecision.Type type) {
        int currentCore = metrics.getCorePoolSize();
        int currentMax = metrics.getMaximumPoolSize();
        int currentQueue = ((ResizableBlockingQueue<?>) getQueue()).getCapacity();

        int coreStep = autoAdjustConfig.getCorePoolStep();
        int maxStep = autoAdjustConfig.getMaxPoolStep();
        int queueStep = autoAdjustConfig.getQueueStep();

        // 慢启动：倍数增长
        int growFactor = Math.max(1, currentGrowthFactor);

        int newCore = currentCore;
        int newMax = currentMax;
        int newQueue = currentQueue;

        // TCP慢启动思想：逐步增加，不能超过最大值
        if (type == AdjustmentDecision.Type.EXPAND_CORE) {
            newCore = Math.min(autoAdjustConfig.getMaxCorePoolSize(), currentCore + coreStep * growFactor);
            // 保证 max >= core
            newMax = Math.max(newCore, Math.min(autoAdjustConfig.getMaxMaximumPoolSize(), currentMax + maxStep * growFactor));
        } else if (type == AdjustmentDecision.Type.EXPAND_MAX) {
            newMax = Math.min(autoAdjustConfig.getMaxMaximumPoolSize(), currentMax + maxStep * growFactor);
            // 同时适度增加 core（可选或保持原样）
            newCore = Math.min(autoAdjustConfig.getMaxCorePoolSize(), currentCore + Math.max(1, coreStep * growFactor / 2));
        } else {
            // 混合
            newCore = Math.min(autoAdjustConfig.getMaxCorePoolSize(), currentCore + coreStep * growFactor);
            newMax = Math.min(autoAdjustConfig.getMaxMaximumPoolSize(), currentMax + maxStep * growFactor);
            newQueue = Math.min(autoAdjustConfig.getMaxQueueCapacity(), currentQueue + queueStep * growFactor);
        }

        return AdjustmentDecision.of(AdjustmentDecision.Type.EXPAND_CORE, newCore, newMax, newQueue, "扩容-" + reason);
    }
    
    /** 创建缩容决策 */
    private AdjustmentDecision createShrinkDecision(ThreadPoolMetrics metrics, String reason) {
        int currentCore = metrics.getCorePoolSize();
        int currentMax = metrics.getMaximumPoolSize();
        int currentQueue = ((ResizableBlockingQueue<?>) getQueue()).getCapacity();

        double shrinkFactor = 0.5; // 乘性缩减系数（AIMD里通常为 0.5）

        int newCore = Math.max(initialCorePoolSize, (int)Math.ceil(currentCore * shrinkFactor));
        int newMax = Math.max(initialMaxPoolSize, (int)Math.ceil(currentMax * shrinkFactor));
        int newQueue = Math.max(initialQueueCapacity, (int)Math.ceil(currentQueue * shrinkFactor));

        // 缩容时重置慢启动因子
        consecutiveOverloadsCounter = 0;
        currentGrowthFactor = 1;

        return AdjustmentDecision.of(AdjustmentDecision.Type.SHRINK_CORE, newCore, newMax, newQueue, "缩容-" + reason);
    }


    private boolean isSystemHealthyForExpansion(SystemMetrics systemMetrics) {
        return systemMetrics.getCpuUsage() < autoAdjustConfig.getMaxCpuUsage() &&
               systemMetrics.getMemoryUsedPercent() < autoAdjustConfig.getMaxMemoryUsage();
    }

    private boolean isQueueNearlyFull(ThreadPoolMetrics metrics) {
        double usageRate = (double) metrics.getQueueSize() /
                           ((ResizableBlockingQueue<?>) getQueue()).getCapacity();
        return usageRate > autoAdjustConfig.getQueueFullThreshold();
    }

    private boolean isPoolIdle(ThreadPoolMetrics metrics) {
        return metrics.getActiveCount() == 0 && metrics.getQueueSize() == 0;
    }
    
    /**
     * 将决策应用到线程池实际参数
     */
    private void applyAdjustment(AdjustmentDecision decision) {
        ThreadPoolMetrics metrics = getThreadPoolMetrics();

        Integer newCore = decision.getNewCorePoolSize();
        Integer newMax = decision.getNewMaximumPoolSize();
        Integer newQueue = decision.getNewQueueCapacity();

        switch (decision.getType()) {
            case EXPAND_CORE:
            case EXPAND_MAX:
                if (newMax != null && getMaximumPoolSize() < newMax) {
                    setMaximumPoolSize(newMax);
                }
                if (newCore != null && getCorePoolSize() < newCore) {
                    setCorePoolSize(newCore);
                }
                if (newQueue != null && ((ResizableBlockingQueue<?>) getQueue()).getCapacity() < newQueue) {
                    ((ResizableBlockingQueue<?>) getQueue()).setCapacity(newQueue);
                }
                break;
            case SHRINK_CORE:
                if (newCore != null && getCorePoolSize() > newCore) {
                    setCorePoolSize(newCore);
                }
                if (newMax != null && getMaximumPoolSize() > newMax) {
                    setMaximumPoolSize(newMax);
                }
                if (newQueue != null && ((ResizableBlockingQueue<?>) getQueue()).getCapacity() > newQueue) {
                    ((ResizableBlockingQueue<?>) getQueue()).setCapacity(newQueue);
                }
                break;
            default:
                break;
        }
    }
    
    private static class TimedRunnable implements Runnable {
        private final Runnable task;
        private final long submitTime;

        private TimedRunnable(Runnable task, long submitTime) {
            this.task = task;
            this.submitTime = submitTime;
        }

        @Override
        public void run() {
            task.run();
        }
    }
    
    public ThreadPoolMetrics getThreadPoolMetrics() {
        return ThreadPoolMetrics.builder()
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .activeCount(getActiveCount())
                .poolSize(getPoolSize())
                .queueSize(getQueue().size())
                .remainingCapacity(getQueue().remainingCapacity())
                .largestPoolSize(getLargestPoolSize())
                .ewmaTaskTime(getEwmaTaskTime())
                .ewmaQueueWait(getEwmaQueueWait())
                .build();
    }
    
    public ThreadPoolConfig getThreadPoolConfig() {
        return ThreadPoolConfig.builder()
                .corePoolSize(getCorePoolSize())
                .maximumPoolSize(getMaximumPoolSize())
                .keepAliveTime(getKeepAliveTime(TimeUnit.SECONDS))
                .allowCoreThreadTimeOut(allowsCoreThreadTimeOut())
                .queueType(getQueue().getClass().getSimpleName())
                .handler(getRejectedExecutionHandler().getClass().getSimpleName())
                .adjustMode(adjustMode.desc)
                .build();
    }

    public long getRejectedExecutionCount() {
        return trackingHandler.getRejectionCount();
    }
}
