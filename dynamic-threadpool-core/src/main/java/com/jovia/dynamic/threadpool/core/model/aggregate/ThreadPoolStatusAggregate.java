package com.jovia.dynamic.threadpool.core.model.aggregate;

import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolMetrics;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author Jay
 * @date 2025-10-27-15:23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ThreadPoolStatusAggregate {

    private ThreadPoolConfig threadPoolConfig;
    private ThreadPoolMetrics threadPoolMetrics;
    
}
