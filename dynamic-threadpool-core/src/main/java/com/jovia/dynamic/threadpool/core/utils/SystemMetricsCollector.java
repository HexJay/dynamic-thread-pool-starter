package com.jovia.dynamic.threadpool.core.utils;

import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import oshi.SystemInfo;
import oshi.hardware.*;

import java.time.LocalDateTime;
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
    private static long[] prevTicks = processor.getSystemCpuLoadTicks();

    // IO状态
    private static long prevDiskRead = 0, prevDiskWrite = 0;
    private static long prevNetRecv = 0, prevNetSent = 0;

    public static SystemMetrics collect(long intervalMillis) throws InterruptedException {
        long start = System.currentTimeMillis();

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
        long idle = ticksAfter[CentralProcessor.TickType.IDLE.getIndex()] -
                    ticksBefore[CentralProcessor.TickType.IDLE.getIndex()];
        long total = Arrays.stream(CentralProcessor.TickType.values())
                .mapToLong(t -> ticksAfter[t.getIndex()] - ticksBefore[t.getIndex()])
                .sum();

        double cpuUsage = (double) (user + sys + ioWait) / total;
        double cpuUserUsage = (double) user / total;
        double cpuSystemUsage = (double) sys / total;
        double cpuIoWaitUsage = (double) ioWait / total;
        double cpuIdleUsage = (double) idle / total;

        // --- Memory ---
        long totalMemory = memory.getTotal();
        long availableMemory = memory.getAvailable();
        double memoryUsedPercent = 1.0 - ((double) availableMemory / totalMemory);

        VirtualMemory vm = memory.getVirtualMemory();

        // --- Disk ---
        long diskRead = 0, diskWrite = 0;
        for (HWDiskStore disk : hal.getDiskStores()) {
            disk.updateAttributes();
            diskRead += disk.getReadBytes();
            diskWrite += disk.getWriteBytes();
        }

        // --- Network ---
        long netRecv = 0, netSent = 0;
        for (NetworkIF net : hal.getNetworkIFs()) {
            net.updateAttributes();
            netRecv += net.getBytesRecv();
            netSent += net.getBytesSent();
        }

        long durationSec = Math.max(1, (System.currentTimeMillis() - start) / 1000);
        long diskReadRate = (diskRead - prevDiskRead) / durationSec;
        long diskWriteRate = (diskWrite - prevDiskWrite) / durationSec;
        long netRecvRate = (netRecv - prevNetRecv) / durationSec;
        long netSentRate = (netSent - prevNetSent) / durationSec;

        prevDiskRead = diskRead;
        prevDiskWrite = diskWrite;
        prevNetRecv = netRecv;
        prevNetSent = netSent;

        // --- System Load ---
        double[] loadAverages = processor.getSystemLoadAverage(3);
        double systemLoadAverage = (loadAverages[0] >= 0) ? loadAverages[0] : 0.0;

        // --- Collect Result ---
        SystemMetrics metrics = new SystemMetrics();
        metrics.setCpuUsage(cpuUsage);
        metrics.setCpuUserUsage(cpuUserUsage);
        metrics.setCpuSystemUsage(cpuSystemUsage);
        metrics.setCpuIoWaitUsage(cpuIoWaitUsage);
        metrics.setCpuIdleUsage(cpuIdleUsage);
        metrics.setCpuLogicalCount(processor.getLogicalProcessorCount());
        metrics.setSystemLoadAverage(systemLoadAverage);

        metrics.setMemoryTotal(totalMemory);
        metrics.setMemoryAvailable(availableMemory);
        metrics.setMemoryUsedPercent(memoryUsedPercent);

        metrics.setDiskReadBytesPerSec(diskReadRate);
        metrics.setDiskWriteBytesPerSec(diskWriteRate);
        metrics.setNetRecvBytesPerSec(netRecvRate);
        metrics.setNetSentBytesPerSec(netSentRate);
        metrics.setTimestamp(LocalDateTime.now());

        return metrics;
    }
}
