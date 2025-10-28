import com.alibaba.fastjson2.JSON;
import com.jovia.dynamic.threadpool.core.model.entity.SystemMetrics;
import com.jovia.dynamic.threadpool.core.utils.SystemMetricsCollector;

/**
 * @author Jay
 * @date 2025-10-27-22:13
 */
public class SysTest {
    public static void main(String[] args) throws InterruptedException {
        SystemMetrics collect = SystemMetricsCollector.collect(1000);
        System.out.println(JSON.toJSONString(collect));
    }
}
