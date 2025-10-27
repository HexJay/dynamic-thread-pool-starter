package com.jovia.dynamic.threadpool.core;

import java.util.concurrent.*;

/**
 * @author Jay
 * @date 2025-10-27-16:44
 */
public class MonitoringThreadPoolExecutor extends ThreadPoolExecutor {
    public MonitoringThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public MonitoringThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public MonitoringThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public MonitoringThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    private volatile double avgTaskTimeMillis = 0.0;
    private static final double ALPHA = 0.3; // EWMA 平滑系数（0~1之间）

    private final ThreadLocal<Long> startTime = new ThreadLocal<>();
    
    private long lastUpdateTime = System.currentTimeMillis();
    
    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        startTime.set(System.nanoTime());
    }
    
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        long endTime = System.nanoTime();
        long durationNs = endTime - startTime.get();
        double durationMs = durationNs / 1_000_000.0;
        updateAvgTaskTime(durationMs);
        startTime.remove();
    }

    private void updateAvgTaskTime(double currentTaskTimeMs) {
        // 指数加权移动平均 (EWMA)
        avgTaskTimeMillis = ALPHA * currentTaskTimeMs + (1 - ALPHA) * avgTaskTimeMillis;
    }
    
    public double getAvgTaskTimeMillis() {
        return avgTaskTimeMillis;
    }
    
    public void setLastUpdateTime(){
        this.lastUpdateTime = System.currentTimeMillis();
    }
    
    public long getLastUpdateTime(){
        return lastUpdateTime;
    }
}
