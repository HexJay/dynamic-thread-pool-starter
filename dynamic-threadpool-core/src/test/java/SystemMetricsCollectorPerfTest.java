import com.jovia.dynamic.threadpool.core.utils.SystemMetricsCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * SystemMetricsCollector 性能损耗测试
 *
 * 说明：
 * - collect(intervalMs) 内部包含 Thread.sleep(intervalMs)，因此这里以
 *   (实际耗时 - intervalMs) 作为“采集额外开销”的近似估计。
 * - 输出均值/中位/95分位/最大值，帮助观察不同采样间隔下的额外损耗。
 */
public class SystemMetricsCollectorPerfTest {

    public static void main(String[] args) throws Exception {
        int[] intervalsMs = new int[]{0, 10, 50, 200};
        int warmup = 3;
        int iterations = 20;

        for (int interval : intervalsMs) {
            runCase(interval, warmup, iterations);
            System.out.println();
        }
    }

    private static void runCase(int intervalMs, int warmup, int iterations) throws Exception {
        // 预热
        for (int i = 0; i < warmup; i++) {
            SystemMetricsCollector.collect(intervalMs);
        }

        List<Double> overheadMs = new ArrayList<>();
        List<Long> memDeltaBytes = new ArrayList<>();

        for (int i = 0; i < iterations; i++) {
            System.gc();
            Thread.sleep(50);

            long usedBefore = usedMemory();
            long startNs = System.nanoTime();

            SystemMetricsCollector.collect(intervalMs);

            long elapsedNs = System.nanoTime() - startNs;
            long usedAfter = usedMemory();

            double elapsedMs = elapsedNs / 1_000_000.0;
            double extra = Math.max(0.0, elapsedMs - intervalMs);
            overheadMs.add(extra);
            memDeltaBytes.add(Math.max(0, usedAfter - usedBefore));
        }

        Collections.sort(overheadMs);
        Collections.sort(memDeltaBytes);

        double avg = average(overheadMs);
        double p50 = percentile(overheadMs, 0.50);
        double p95 = percentile(overheadMs, 0.95);
        double max = overheadMs.isEmpty() ? 0.0 : overheadMs.get(overheadMs.size() - 1);

        long memP50 = percentileLong(memDeltaBytes, 0.50);
        long memP95 = percentileLong(memDeltaBytes, 0.95);
        long memMax = memDeltaBytes.isEmpty() ? 0L : memDeltaBytes.get(memDeltaBytes.size() - 1);

        System.out.println("==== SystemMetricsCollector 性能(间隔=" + intervalMs + " ms) ====");
        System.out.printf("额外开销(去除sleep) 平均: %.3f ms, 中位: %.3f ms, P95: %.3f ms, 最大: %.3f ms%n",
                avg, p50, p95, max);
        System.out.printf("内存增量(近似) 中位: %d B, P95: %d B, 最大: %d B%n",
                memP50, memP95, memMax);
    }

    private static long usedMemory() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static double average(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) sum += v;
        return sum / values.size();
    }

    private static double percentile(List<Double> sortedValues, double p) {
        if (sortedValues.isEmpty()) return 0.0;
        int idx = (int) Math.ceil(p * sortedValues.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sortedValues.size()) idx = sortedValues.size() - 1;
        return sortedValues.get(idx);
    }

    private static long percentileLong(List<Long> sortedValues, double p) {
        if (sortedValues.isEmpty()) return 0L;
        int idx = (int) Math.ceil(p * sortedValues.size()) - 1;
        if (idx < 0) idx = 0;
        if (idx >= sortedValues.size()) idx = sortedValues.size() - 1;
        return sortedValues.get(idx);
    }
}


