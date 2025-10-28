package com.jovia.dynamic.threadpool.core.domain.pool;

import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * @author Jay
 * @date 2025-10-27-16:44
 */
@Slf4j
public class DynamicThreadPoolExecutor extends ThreadPoolExecutor {
    
    public DynamicThreadPoolExecutor(int corePoolSize, int maximumPoolSize,
                                     long keepAliveTime, TimeUnit unit, int capacity,
                                     ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, new ResizableBlockingQueue<>(capacity), threadFactory, handler);
    }
    
    @Getter
    private volatile double ewmaTaskTime = 0.0; // 平滑任务执行时间
    @Getter
    private volatile double ewmaQueueWait = 0.0; // 平滑队列任务等待时间

    private static final double ALPHA = 0.3; // EWMA 平滑系数（0~1之间）

    private final ThreadLocal<Long> startTime = new ThreadLocal<>();

    @Getter
    private long lastUpdateTime = System.currentTimeMillis();
    @Getter
    private String adjustMode = ThreadPoolConfig.Mode.MANUAL.desc;


    @Override
    public void execute(Runnable command) {
        long now = System.nanoTime();
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
            CompletableFuture<String> c1 = new CompletableFuture<>();
            c1.exceptionally(err -> {
                log.error("err");
                return err.getCause().getMessage();
            });
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
                .adjustMode(adjustMode)
                .build();
    }
}
