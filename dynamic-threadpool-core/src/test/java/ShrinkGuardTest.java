import com.jovia.dynamic.threadpool.core.domain.pool.DynamicThreadPoolExecutor;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import com.jovia.dynamic.threadpool.core.model.vo.AdjustMode;
import com.jovia.dynamic.threadpool.core.model.vo.AutoAdjustConfig;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsMonitor;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class ShrinkGuardTest {

    public static void main(String[] args) throws Exception {
        SystemMetricsMonitor.start(100);

        int initCore = 2;
        int initMax = 4;
        int initQueue = 20;

        ThreadFactory tf = Executors.defaultThreadFactory();
        DynamicThreadPoolExecutor exec = new DynamicThreadPoolExecutor(
                initCore, initMax, 60, TimeUnit.SECONDS, initQueue, tf,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );

        AutoAdjustConfig cfg = AutoAdjustConfig.builder()
                .adjustIntervalMs(300)
                .queueWaitThresholdMs(5)
                .queueFullThreshold(0.5)
                .idleShrinkThresholdMs(1500)
                .corePoolStep(2)
                .maxPoolStep(4)
                .queueStep(10)
                .maxCorePoolSize(32)
                .maxMaximumPoolSize(64)
                .maxQueueCapacity(500)
                .useSystemMetrics(true)
                .allowShrink(true)
                .build();
        set(exec, "autoAdjustConfig", cfg);
        set(exec, "adjustMode", AdjustMode.AUTO);

        System.out.printf("[INIT] core=%d, max=%d\n", exec.getCorePoolSize(), exec.getMaximumPoolSize());

        // Phase A: 未扩容情况下的空闲 -> 不应发生缩容
        phaseIdle(exec, 4000);
        ThreadPoolMetrics a = exec.getThreadPoolMetrics();
        System.out.printf("[A-IDLE] core=%d, max=%d (expect no shrink below initial %d/%d)\n",
                a.getCorePoolSize(), a.getMaximumPoolSize(), initCore, initMax);

        // Phase B: 高负载触发扩容
        phaseLoad(exec, 6000);
        ThreadPoolMetrics b = exec.getThreadPoolMetrics();
        boolean expanded = b.getCorePoolSize() > initCore || b.getMaximumPoolSize() > initMax;
        System.out.printf("[B-LOAD] core=%d, max=%d (expanded=%s)\n", b.getCorePoolSize(), b.getMaximumPoolSize(), expanded);

        // Phase C: 扩容后空闲 -> 允许缩容，但不得低于初始
        phaseIdle(exec, 6000);
        ThreadPoolMetrics c = exec.getThreadPoolMetrics();
        boolean notBelowInit = c.getCorePoolSize() >= initCore && c.getMaximumPoolSize() >= initMax;
        boolean shrunk = expanded && (c.getCorePoolSize() < b.getCorePoolSize() || c.getMaximumPoolSize() < b.getMaximumPoolSize());
        System.out.printf("[C-IDLE] core=%d, max=%d (shrunk=%s, notBelowInit=%s)\n",
                c.getCorePoolSize(), c.getMaximumPoolSize(), shrunk, notBelowInit);

        exec.shutdownNow();
    }

    private static void phaseIdle(DynamicThreadPoolExecutor exec, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            exec.execute(() -> {}); // 轻量任务用于触发 afterExecute
            Thread.sleep(50);
        }
    }

    private static void phaseLoad(DynamicThreadPoolExecutor exec, long millis) throws InterruptedException {
        long end = System.currentTimeMillis() + millis;
        while (System.currentTimeMillis() < end) {
            exec.execute(() -> {
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
            });
        }
    }

    private static void set(Object target, String field, Object val) throws Exception {
        Field f = target.getClass().getDeclaredField(field);
        f.setAccessible(true);
        f.set(target, val);
    }
}
