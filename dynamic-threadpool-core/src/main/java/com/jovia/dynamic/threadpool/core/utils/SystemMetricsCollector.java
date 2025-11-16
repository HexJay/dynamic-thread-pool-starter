package com.jovia.dynamic.threadpool.core.utils;

import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import oshi.SystemInfo;
import oshi.hardware.*;

import java.util.Arrays;

/**
 * @author Jay
 * @date 2025-10-27-16:01
 */
public class SystemMetricsCollector {

    private static final SystemInfo systemInfo = new SystemInfo();
    private static final HardwareAbstractionLayer hal = systemInfo.getHardware();
    private static final CentralProcessor processor = hal.getProcessor();
    private static final GlobalMemory memory = hal.getMemory();
    
    public static SystemMetrics collect(long intervalMillis) throws InterruptedException {

        // --- CPU ---
        long[] ticksBefore = processor.getSystemCpuLoadTicks();
        Thread.sleep(intervalMillis);
        long[] ticksAfter = processor.getSystemCpuLoadTicks();

        long user = ticksAfter[CentralProcessor.TickType.USER.getIndex()] -
                    ticksBefore[CentralProcessor.TickType.USER.getIndex()];
        long sys = ticksAfter[CentralProcessor.TickType.SYSTEM.getIndex()] -
                   ticksBefore[CentralProcessor.TickType.SYSTEM.getIndex()];
        long ioWait = ticksAfter[CentralProcessor.TickType.IOWAIT.getIndex()] -
                      ticksBefore[CentralProcessor.TickType.IOWAIT.getIndex()];
        long total = Arrays.stream(CentralProcessor.TickType.values())
                .mapToLong(t -> ticksAfter[t.getIndex()] - ticksBefore[t.getIndex()])
                .sum();

        double cpuUsage = (double) (user + sys + ioWait) / total;

        // --- Memory ---
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        double memoryUsedPercent = 1.0 - ((double) availableMemory / totalMemory);

        // --- Collect Result ---
        SystemMetrics metrics = new SystemMetrics();
        metrics.setCpuUsage(cpuUsage);
        metrics.setMemoryUsedPercent(memoryUsedPercent);

        return metrics;
    }
}
