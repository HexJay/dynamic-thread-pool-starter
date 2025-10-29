package com.jovia.dynamic.threadpool.core.domain.pool;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 增强的拒绝策略，用于跟踪拒绝次数
 * @author Jay
 * @date 2025-10-29-19:38
 */
public class MetricsTrackingRejectedExecutionHandler implements RejectedExecutionHandler {

    private final RejectedExecutionHandler delegate;
    private final AtomicLong rejectionCount = new AtomicLong(0);

    public MetricsTrackingRejectedExecutionHandler(RejectedExecutionHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        rejectionCount.incrementAndGet();
        delegate.rejectedExecution(r, executor);
    }

    public long getRejectionCount() {
        return rejectionCount.get();
    }

    public void resetRejectionCount() {
        rejectionCount.set(0);
    }
}
