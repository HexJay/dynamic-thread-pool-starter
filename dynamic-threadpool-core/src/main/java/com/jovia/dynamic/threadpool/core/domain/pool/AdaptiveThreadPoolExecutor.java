package com.jovia.dynamic.threadpool.core.domain.pool;

import com.jovia.dynamic.threadpool.core.model.entity.AdjustmentDecision;
import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.model.vo.AdjustMode;
import com.jovia.dynamic.threadpool.core.model.vo.AutoAdjustConfig;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 动态线程池执行器
 * 支持自动调整线程池参数
 * 
 * @author Jay
 * @date 2025-10-27-16:44
 */
@Slf4j
public class AdaptiveThreadPoolExecutor extends ThreadPoolExecutor {

    private final MetricsTrackingRejectedExecutionHandler trackingHandler;
    private final ReentrantLock adjustLock = new ReentrantLock();
    
    
    // 初始参数记录
    private final int initialCorePoolSize;
    private final int initialMaxPoolSize;
    private final int initialQueueCapacity;

    // 指标跟踪
    private static final double ALPHA = 0.3; // EWMA 平滑系数
    @Getter
    private volatile double execTime = 0.0; // 平滑任务执行时间
    @Getter
    private volatile double waitTime = 0.0; // 平滑队列任务等待时间
    @Getter
    private final AtomicInteger activeThreads = new AtomicInteger(0);
    
    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    // 调整相关
    @Getter
    private volatile AdjustMode adjustMode = AdjustMode.MANUAL;
    
    public AdaptiveThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                      long keepAliveTime, TimeUnit unit, int capacity,
                                      ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, 
              new ResizableBlockingQueue<>(capacity), threadFactory,
              new MetricsTrackingRejectedExecutionHandler(handler));
        // 记录拒绝策略
        this.trackingHandler = (MetricsTrackingRejectedExecutionHandler) getRejectedExecutionHandler();
        // 记录初始参数
        this.initialCorePoolSize = corePoolSize;
        this.initialMaxPoolSize = maximumPoolSize;
        this.initialQueueCapacity = capacity;
    }
    
    @Override
    public void execute(Runnable task) {
        super.execute(wrap(task));
    }

    @Override
    public Future<?> submit(Runnable task) {
        return super.submit(wrap(task));
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        // 增加活跃线程数
        activeThreads.incrementAndGet();
        
        // 所有通过 execute/submit 提交的任务都已被 wrap 包装为 TimedRunnable
        TimedRunnable tr = (TimedRunnable) r;
        long waitTimeNanos = System.nanoTime() - tr.submitTime;
        double waitTimeMillis = waitTimeNanos / 1_000_000.0;
        waitTime = ALPHA * waitTimeMillis + (1 - ALPHA) * waitTime;
        
        startTime.set(System.nanoTime());
        super.beforeExecute(t, r);
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        try {
            long endTime = System.nanoTime();
            long durationNs = endTime - startTime.get();
            double durationMs = durationNs / 1_000_000.0;
            execTime = ALPHA * durationMs + (1 - ALPHA) * execTime;
        } finally {
            // 减少活跃线程数
            activeThreads.decrementAndGet();
            startTime.remove();
        }

        // 在任务执行后检查是否需要调整（仅在自动模式下）
        if (adjustMode == AdjustMode.AUTO) {
            adjustmentIfNeeded();
        }
    }
    
    // 包装任务
    private Runnable wrap(Runnable r) {
        if(r instanceof TimedRunnable){
            return r;
        }
        return new TimedRunnable(r, System.nanoTime());
    }
    

    private void adjustmentIfNeeded() {

    }
    
    
    /**
     * 应用调整决策到线程池
     */
    private void applyAdjustment(AdjustmentDecision decision) {

    }
    
    // 保存提交时间的任务
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
                .ewmaTaskTime(getExecTime())
                .ewmaQueueWait(getWaitTime())
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

    // 获取拒绝次数
    public long getRejectedExecutionCount() {
        return trackingHandler.getRejectionCount();
    }
    
    // 获取队列数量
    public int getQueueSize() {
        return getQueue().size();
    }
}
