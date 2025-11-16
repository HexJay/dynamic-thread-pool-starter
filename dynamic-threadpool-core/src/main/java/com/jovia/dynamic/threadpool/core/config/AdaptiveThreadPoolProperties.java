package com.jovia.dynamic.threadpool.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Jay
 * @date 2025-10-19-15:55
 */
@Data
@ConfigurationProperties(prefix = "adaptive.thread.pool", ignoreInvalidFields = true)
public class AdaptiveThreadPoolProperties {

    /**
     * 状态；open = 开启、close 关闭
     */
    private boolean enabled;

    /**
     * 配置中心选择
     */
    private String configCenter;
    
}
