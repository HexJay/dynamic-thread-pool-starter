package com.jovia.dynamic.threadpool.core.domain.pool;

import lombok.Getter;
import lombok.NonNull;

import java.util.concurrent.LinkedBlockingQueue;

/**
 * 尅可调节大小的队列
 *
 * @author Jay
 * @date 2025-10-28-22:39
 */
@Getter
public class ResizableBlockingQueue<E> extends LinkedBlockingQueue<E> {

    private volatile int capacity;
    
    public ResizableBlockingQueue(int capacity) {
        super(capacity);
        this.capacity = capacity;
    }

    /**
     * 容量更新
     */
    public synchronized void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public int remainingCapacity() {
        return capacity - size();
    }
    
    @Override
    public boolean offer(@NonNull E e) {
        if (size() >= capacity) {
            return false;
        }
        return super.offer(e);
    }
}
