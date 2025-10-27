package com.jovia.dynamic.threadpool.registry.redis.trigger.listener;

import com.alibaba.fastjson.JSON;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import com.jovia.dynamic.threadpool.core.service.IDynamicThreadPoolService;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Jay
 * @date 2025-10-19-20:51
 */
public class ThreadPoolConfigAdjustListener implements MessageListener<String> {

    private final Logger logger = LoggerFactory.getLogger(ThreadPoolConfigAdjustListener.class);
    
    private final IDynamicThreadPoolService dynamicThreadPoolService;

    public ThreadPoolConfigAdjustListener(IDynamicThreadPoolService dynamicThreadPoolService) {
        this.dynamicThreadPoolService = dynamicThreadPoolService;
    }

    @Override
    public void onMessage(CharSequence channel, String msg) {
        ThreadPoolConfig config = JSON.parseObject(msg, ThreadPoolConfig.class);

        logger.info("动态线程池 {} 配置更新, corePoolSize:{}, maximumPoolSize:{}", config.getThreadPoolName(), config.getCorePoolSize(), config.getMaximumPoolSize());
        dynamicThreadPoolService.updateThreadPoolConfig(config);
    }
}
