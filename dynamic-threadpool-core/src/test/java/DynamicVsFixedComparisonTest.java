import com.jovia.dynamic.threadpool.core.domain.pool.DynamicThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import com.jovia.dynamic.threadpool.core.model.vo.AdjustMode;
import com.jovia.dynamic.threadpool.core.model.vo.AutoAdjustConfig;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

/**
 * 动态线程池 vs 固定线程池 对比测试
 *
 * 指标：
 * - 队列等待时间(ms)：均值/中位/P95/最大
 * - 服务时间(ms)：均值/中位/P95/最大（任务本身执行时长）
 * - 吞吐量：完成任务数 / 秒
 * - CPU 使用率：中位 / P95（期间采样）
 * - 内存增量：阶段内 usedHeap 的增量估计
 * - 拒绝次数：被拒任务次数（CallerRuns 也算一次拒绝）
 */
public class DynamicVsFixedComparisonTest {

    public static void main(String[] args) throws Exception {
        System.out.println("== 启动系统指标采样 ==");
        // 更长采样间隔，避免 OSHI 在极短间隔下出现 total=0 导致 NaN
        SystemMetricsMonitor.start(200);

        // 负载模型：交替重/轻负载
        Workload workload = new Workload(
                500,       // 重负载任务休眠(ms) — 缩短以增加 afterExecute 频率，便于自适应
                8,         // 重负载秒数
                6,         // 轻负载秒数
                10, 40     // 每秒提交速率：轻负载 ~10 tps，重负载 ~40 tps
        );

        System.out.println("== 阶段A：固定线程池 ==");
        PhaseResult fixed = runFixedPoolPhase(workload, 40);
        printResult("固定线程池", fixed);

        System.out.println();
        System.out.println("== 阶段B：动态线程池(AUTO) ==");
        PhaseResult dynamic = runDynamicPoolPhase(workload, 40);
        printResult("动态线程池", dynamic);

        System.out.println();
        System.out.println("== 对比小结 ==");
        compare(fixed, dynamic);
    }

    // ---------- 阶段实现 ----------

    private static PhaseResult runFixedPoolPhase(Workload workload, int seconds) throws Exception {
        LinkedBlockingQueue<Runnable> queue = new LinkedBlockingQueue<>(100);
        RejectedCounterHandler handler = new RejectedCounterHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        InstrumentedThreadPoolExecutor executor = new InstrumentedThreadPoolExecutor(
                8, 8, 60, TimeUnit.SECONDS, queue, Executors.defaultThreadFactory(), handler
        );

        try {
            return runPhase(executor, handler, workload, seconds);
        } finally {
            executor.shutdownNow();
        }
    }

    private static PhaseResult runDynamicPoolPhase(Workload workload, int seconds) throws Exception {
        RejectedCounterHandler handler = new RejectedCounterHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        DynamicThreadPoolExecutor executor = new DynamicThreadPoolExecutor(
                2, 4, 60, TimeUnit.SECONDS,
                100, Executors.defaultThreadFactory(), handler
        );

        AutoAdjustConfig cfg = AutoAdjustConfig.builder()
                .adjustIntervalMs(300)
                .queueWaitThresholdMs(5)
                .queueFullThreshold(0.5)
                .idleShrinkThresholdMs(3_000)
                .corePoolStep(2)
                .maxPoolStep(4)
                .queueStep(50)
                .maxCorePoolSize(48)
                .maxMaximumPoolSize(96)
                .maxQueueCapacity(1000)
                .useSystemMetrics(true)
                .allowShrink(true)
                .build();

        setPrivateField(executor, "autoAdjustConfig", cfg);
        setPrivateField(executor, "adjustMode", AdjustMode.AUTO);

        try {
            return runPhase(executor, handler, workload, seconds);
        } finally {
            executor.shutdownNow();
        }
    }

    private static PhaseResult runPhase(ThreadPoolExecutor executor,
                                        RejectedCounterHandler handler,
                                        Workload workload,
                                        int seconds) throws Exception {
        MetricsAgg agg = new MetricsAgg();

        long memBefore = usedMemory();
        long start = System.currentTimeMillis();
        long endAt = start + seconds * 1000L;

        // CPU采样线程
        List<Double> cpuList = new ArrayList<>();
        Thread cpuSampler = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                SystemMetrics sm = SystemMetricsMonitor.getLastSystemMetrics();
                if (sm != null && !Double.isNaN(sm.getCpuUsage())) cpuList.add(sm.getCpuUsage());
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
            }
        });
        cpuSampler.setDaemon(true);
        cpuSampler.start();

        // 负载线程：持续提交
        Thread producer = new Thread(() -> {
            workload.runUntil(endAt, task -> {
                long submitNs = System.nanoTime();
                Runnable r = () -> {
                    long startNs = System.nanoTime();
                    double waitMs = (startNs - submitNs) / 1_000_000.0;
                    agg.addWait(waitMs);
                    // 模拟任务执行
                    try {
                        Thread.sleep(task.durationMs);
                    } catch (InterruptedException ignored) {
                    }
                    double svcMs = (System.nanoTime() - startNs) / 1_000_000.0;
                    agg.addService(svcMs);
                    agg.incCompleted();
                };
                try {
                    executor.execute(r);
                } catch (RejectedExecutionException ignored) {
                    handler.incRejected();
                }
            });
        });
        producer.setDaemon(true);
        producer.start();

        // 等待结束
        while (System.currentTimeMillis() < endAt) {
            Thread.sleep(200);
        }
        producer.interrupt();
        cpuSampler.interrupt();

        long memAfter = usedMemory();
        long elapsedMs = System.currentTimeMillis() - start;

        // 结果聚合
        PhaseResult result = new PhaseResult();
        result.elapsedMs = elapsedMs;
        result.completed = agg.completed;
        result.rejected = handler.getRejected();
        result.throughput = agg.completed * 1000.0 / Math.max(1, elapsedMs);
        result.waitStats = Stats.of(agg.waitList);
        result.svcStats = Stats.of(agg.svcList);
        result.cpuMedian = percentile(cpuList, 0.5);
        result.cpuP95 = percentile(cpuList, 0.95);
        result.memoryDeltaBytes = Math.max(0, memAfter - memBefore);

        return result;
    }

    // ---------- 数据结构 ----------

    private static class Workload {
        final int heavyTaskMs;
        final int heavySeconds;
        final int lightSeconds;
        final int lightRatePerSec;
        final int heavyRatePerSec;
        final Random rnd = new Random();

        Workload(int heavyTaskMs, int heavySeconds, int lightSeconds, int lightRatePerSec, int heavyRatePerSec) {
            this.heavyTaskMs = heavyTaskMs;
            this.heavySeconds = heavySeconds;
            this.lightSeconds = lightSeconds;
            this.lightRatePerSec = lightRatePerSec;
            this.heavyRatePerSec = heavyRatePerSec;
        }

        interface Submitter { void submit(Task t); }

        static class Task { final int durationMs; Task(int d){ this.durationMs = d; } }

        void runUntil(long endAt, Submitter submitter) {
            boolean heavy = true;
            long phaseStart = System.currentTimeMillis();
            int phaseLen = heavy ? heavySeconds : lightSeconds;
            int rate = heavy ? heavyRatePerSec : lightRatePerSec;
            long nextTick = System.currentTimeMillis();

            while (System.currentTimeMillis() < endAt && !Thread.currentThread().isInterrupted()) {
                long now = System.currentTimeMillis();
                if (now - phaseStart >= phaseLen * 1000L) {
                    heavy = !heavy;
                    phaseStart = now;
                    phaseLen = heavy ? heavySeconds : lightSeconds;
                    rate = heavy ? heavyRatePerSec : lightRatePerSec;
                }

                // 每秒 rate 个任务，均匀提交
                nextTick += 1000;
                int toSubmit = rate;
                while (toSubmit-- > 0) {
                    int duration = heavy ? heavyTaskMs : 5 + rnd.nextInt(10);
                    submitter.submit(new Task(duration));
                }

                long sleepMs = Math.max(1, nextTick - System.currentTimeMillis());
                try { Thread.sleep(sleepMs); } catch (InterruptedException e) { break; }
            }
        }
    }

    private static class RejectedCounterHandler implements RejectedExecutionHandler {
        private final RejectedExecutionHandler delegate;
        private volatile long rejected = 0;
        RejectedCounterHandler(RejectedExecutionHandler delegate){ this.delegate = delegate; }
        @Override public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) { rejected++; delegate.rejectedExecution(r, executor); }
        void incRejected(){ rejected++; }
        long getRejected(){ return rejected; }
    }

    private static class InstrumentedThreadPoolExecutor extends ThreadPoolExecutor {
        InstrumentedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        }
    }

    private static class MetricsAgg {
        final List<Double> waitList = Collections.synchronizedList(new ArrayList<>());
        final List<Double> svcList = Collections.synchronizedList(new ArrayList<>());
        volatile long completed = 0;
        void addWait(double v){ waitList.add(v); }
        void addService(double v){ svcList.add(v); }
        void incCompleted(){ completed++; }
    }

    private static class Stats {
        final double avg, p50, p95, max;
        Stats(double avg, double p50, double p95, double max){ this.avg=avg; this.p50=p50; this.p95=p95; this.max=max; }
        static Stats of(List<Double> ls){
            if (ls.isEmpty()) return new Stats(0,0,0,0);
            List<Double> copy = new ArrayList<>(ls);
            Collections.sort(copy);
            double sum = 0; for(double v:copy) sum+=v;
            return new Stats(sum/copy.size(), percentile(copy,0.5), percentile(copy,0.95), copy.get(copy.size()-1));
        }
        @Override public String toString(){
            return String.format("avg=%.2fms, p50=%.2fms, p95=%.2fms, max=%.2fms", avg, p50, p95, max);
        }
    }

    // ---------- 打印/对比 ----------

    private static void printResult(String title, PhaseResult r){
        System.out.println("[" + title + "]");
        System.out.printf("elapsed=%d ms, completed=%d, throughput=%.2f tps, rejected=%d%n", r.elapsedMs, r.completed, r.throughput, r.rejected);
        System.out.println("wait   : " + r.waitStats);
        System.out.println("service: " + r.svcStats);
        System.out.printf("cpu median=%.2f%%, cpu p95=%.2f%%%n", r.cpuMedian*100, r.cpuP95*100);
        System.out.printf("memory delta=%.2f MB%n", r.memoryDeltaBytes/1024.0/1024.0);
    }

    private static void compare(PhaseResult a, PhaseResult b){
        System.out.printf("吞吐量提升: %.2f%%%n", pctImprove(a.throughput, b.throughput));
        System.out.printf("等待时间P95改善: %.2f%%%n", pctReduce(a.waitStats.p95, b.waitStats.p95));
        System.out.printf("CPU P95变化: %.2f%%%n", pctReduce(a.cpuP95, b.cpuP95));
        System.out.printf("内存增量变化: %.2f%%%n", pctReduce(a.memoryDeltaBytes, b.memoryDeltaBytes));
        System.out.printf("拒绝次数变化: %.2f%%%n", pctReduce(a.rejected, b.rejected));
    }

    private static class PhaseResult {
        long elapsedMs;
        long completed;
        double throughput;
        long rejected;
        Stats waitStats;
        Stats svcStats;
        double cpuMedian;
        double cpuP95;
        long memoryDeltaBytes;
    }

    // ---------- 工具 ----------

    private static double percentile(List<Double> values, double p){
        if (values.isEmpty()) return 0.0;
        List<Double> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return percentileSorted(copy, p);
    }
    private static double percentileSorted(List<Double> sorted, double p){
        if (sorted.isEmpty()) return 0.0;
        int idx = (int)Math.ceil(p*sorted.size()) - 1;
        if (idx < 0) idx=0; if (idx >= sorted.size()) idx = sorted.size()-1;
        return sorted.get(idx);
    }
    private static double pctImprove(double base, double now){ if (base <= 0) return 0; return (now-base)/base*100.0; }
    private static double pctReduce(double base, double now){ if (base <= 0) return 0; return (base-now)/base*100.0; }
    private static double pctReduce(long base, long now){ if (base <= 0) return 0; return (base-now)*100.0/base; }

    private static long usedMemory(){ Runtime rt = Runtime.getRuntime(); return rt.totalMemory() - rt.freeMemory(); }

    private static void setPrivateField(Object target, String field, Object val) throws Exception {
        java.lang.reflect.Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }
}


