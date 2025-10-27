package com.jovia.dynamic.threadpool.core.trigger.job;

import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * @author Jay
 * @date 2025-10-25-22:44
 */
public class AdaptiveAdjusterJob {
    private final Logger logger = LoggerFactory.getLogger(AdaptiveAdjusterJob.class);

    private final IDynamicThreadPoolService dynamicThreadPoolService;
    
    public AdaptiveAdjusterJob(IDynamicThreadPoolService dynamicThreadPoolService) {
        this.dynamicThreadPoolService = dynamicThreadPoolService;
    }
    
    @Scheduled(cron = "0 * * * * *")
    public void adjust() {
        dynamicThreadPoolService.adjustIfNeeded();
    }
}
