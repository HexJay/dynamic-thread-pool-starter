package com.jovia.dynamic.threadpool.core.domain.pool;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * 自适应线程池启动器
 * 
 * @author Jay
 * @date 2025-11-13-10:04
 */
@Component
public class AdaptiveThreadPoolStarter implements ApplicationContextAware {
    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        // TODO: 初始化自适应调整相关组件
    }
}
