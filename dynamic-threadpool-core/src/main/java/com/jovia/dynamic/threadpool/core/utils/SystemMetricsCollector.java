package com.jovia.dynamic.threadpool.core.utils;

import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;

import java.time.LocalDateTime;

/**
 * @author Jay
 * @date 2025-10-27-16:01
 */
public class SystemMetricsCollector {
    private static final SystemInfo systemInfo = new SystemInfo();
    private static final CentralProcessor processor = systemInfo.getHardware().getProcessor();
    private static final GlobalMemory memory = systemInfo.getHardware().getMemory();

    private static long[] prevTicks = processor.getSystemCpuLoadTicks();

    public static SystemMetrics collect() throws InterruptedException {
        long[] ticks = processor.getSystemCpuLoadTicks();
        double cpuUsage = processor.getSystemCpuLoadBetweenTicks(prevTicks);
        prevTicks = ticks;

        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();

        SystemMetrics metrics = new SystemMetrics();
        metrics.setCpuUsage(cpuUsage);
        metrics.setCpuLogicalCount(processor.getLogicalProcessorCount());
        metrics.setCpuUserUsage(processor.getSystemCpuLoadTicks()[CentralProcessor.TickType.USER.getIndex()]);
        metrics.setCpuSystemUsage(processor.getSystemCpuLoadTicks()[CentralProcessor.TickType.SYSTEM.getIndex()]);
        metrics.setCpuIdle(processor.getSystemCpuLoadTicks()[CentralProcessor.TickType.IDLE.getIndex()]);
        
        // 获取负载平均值（1, 5, 15 分钟）
        double[] loadAverages = processor.getSystemLoadAverage(3);
        double systemLoadAverage = loadAverages[0] >= 0 ? loadAverages[0] : 0.0;
        metrics.setSystemLoadAverage(systemLoadAverage);
        
        metrics.setMemoryTotal(totalMemory);
        metrics.setMemoryAvailable(availableMemory);
        metrics.setMemoryUsedPercent(1.0 - ((double) availableMemory / totalMemory));
        metrics.setTimestamp(LocalDateTime.now());

        return metrics;
    }
}
