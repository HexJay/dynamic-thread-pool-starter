import com.jovia.dynamic.threadpool.core.domain.pool.DynamicThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import com.jovia.dynamic.threadpool.core.model.entity.AdjustmentDecision;
import com.jovia.dynamic.threadpool.core.model.vo.AdjustMode;
import com.jovia.dynamic.threadpool.core.model.vo.AutoAdjustConfig;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;
import com.jovia.dynamic.threadpool.core.domain.pool.ResizableBlockingQueue;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AutoAdjustabilityTest {

    public static void main(String[] args) throws Exception {
        // 启动系统指标采集守护线程，供自动调整读取
        SystemMetricsMonitor.start(50);

        ThreadFactory tf = Executors.defaultThreadFactory();
        DynamicThreadPoolExecutor executor = new DynamicThreadPoolExecutor(
                50, // core
                100, // max
                60, TimeUnit.SECONDS,
                10, // queue capacity
                tf,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        // 配置自动调整：缩短调整间隔、降低触发阈值、限制上限便于观察
        AutoAdjustConfig cfg = AutoAdjustConfig.builder()
                .adjustIntervalMs(500)
                .queueWaitThresholdMs(5)
                .queueFullThreshold(0.4)
                .queueWaitLowMs(1)
                .queueWaitWeight(0.5)
                .idleShrinkThresholdMs(2_000)
                .maxCpuUsage(0.95)
                .maxMemoryUsage(0.95)
                .corePoolStep(1)
                .maxPoolStep(1)
                .queueStep(10)
                .maxCorePoolSize(16)
                .maxMaximumPoolSize(32)
                .maxQueueCapacity(200)
                .useSystemMetrics(true)
                .allowShrink(true)
                .build();

        setPrivateField(executor, "autoAdjustConfig", cfg);
        setPrivateField(executor, "adjustMode", AdjustMode.AUTO);

        System.out.println("[INIT] " + dump(executor.getThreadPoolMetrics(), queueCapacity(executor)));

        // 持续负载生成器：交替高负载(10s) 和 低负载(10s)
        Thread producer = new Thread(() -> {
            long phaseMs = 10_000;
            boolean heavy = true;
            while (!Thread.currentThread().isInterrupted()) {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < phaseMs) {
                    try {
                        if (heavy) {
                            executor.execute(() -> {
                                try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                            });
                        } else {
                            executor.execute(() -> {
                                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                            });
                            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
                        }
                    } catch (Exception ignored) {
                    }
                }
                heavy = !heavy;
            }
        });
        producer.setDaemon(true);
        producer.start();

        // 采样器：定期打印“建议决策”和“已应用参数变化”
        Thread sampler = new Thread(() -> {
            int lastCore = executor.getCorePoolSize();
            int lastMax = executor.getMaximumPoolSize();
            int lastCap = queueCapacity(executor);
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500);
                    ThreadPoolMetrics pm = executor.getThreadPoolMetrics();
                    SystemMetrics sm = SystemMetricsMonitor.getLastSystemMetrics();
                    AdjustmentDecision decision = reflectDecision(executor, pm, sm);

                    int curCore = executor.getCorePoolSize();
                    int curMax = executor.getMaximumPoolSize();
                    int curCap = queueCapacity(executor);

                    if (decision != null) {
                        System.out.println("[DECIDE] " + decision.getType() + " - " + decision.getReason());
                    }
                    if (curCore != lastCore || curMax != lastMax || curCap != lastCap) {
                        System.out.println("[APPLIED] " + dump(pm, curCap));
                        lastCore = curCore;
                        lastMax = curMax;
                        lastCap = curCap;
                    } else {
                        System.out.println("[SNAP]   " + dump(pm, curCap));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception ignored) {
                }
            }
        });
        sampler.setDaemon(true);
        sampler.start();

        // 运行 60 秒后结束
        Thread.sleep(60_000);
        executor.shutdownNow();
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static String dump(ThreadPoolMetrics m, int capacity) {
        return String.format(
                "core=%d, max=%d, active=%d, pool=%d, q=%d/%d, rem=%d, ewmaWait=%.2fms, ewmaTime=%.2fms",
                m.getCorePoolSize(), m.getMaximumPoolSize(), m.getActiveCount(), m.getPoolSize(),
                m.getQueueSize(), capacity, m.getRemainingCapacity(), m.getEwmaQueueWait(), m.getEwmaTaskTime()
        );
    }

    private static int queueCapacity(DynamicThreadPoolExecutor exec) {
        try {
            return ((ResizableBlockingQueue<?>) exec.getQueue()).getCapacity();
        } catch (Exception e) {
            return -1;
        }
    }

    private static AdjustmentDecision reflectDecision(DynamicThreadPoolExecutor exec,
                                                      ThreadPoolMetrics pm,
                                                      SystemMetrics sm) throws Exception {
        try {
            java.lang.reflect.Method m = DynamicThreadPoolExecutor.class.getDeclaredMethod("createAdjustmentDecision",
                    ThreadPoolMetrics.class, SystemMetrics.class);
            m.setAccessible(true);
            Object ret = m.invoke(exec, pm, sm);
            return (AdjustmentDecision) ret;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}


