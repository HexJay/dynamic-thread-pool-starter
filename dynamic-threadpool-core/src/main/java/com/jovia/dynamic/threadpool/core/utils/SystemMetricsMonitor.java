package com.jovia.dynamic.threadpool.core.utils;

import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import lombok.Getter;

/**
 * @author Jay
 * @date 2025-10-29-23:13
 */
public class SystemMetricsMonitor {
    
    @Getter
    private static volatile SystemMetrics lastSystemMetrics;
    
    public static void start(long intervalMs) {
        Thread t = new Thread(() -> {
            while (true) {
                try{
                    lastSystemMetrics = SystemMetricsCollector.collect(intervalMs);
                }catch (Exception ignored){
                    
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
