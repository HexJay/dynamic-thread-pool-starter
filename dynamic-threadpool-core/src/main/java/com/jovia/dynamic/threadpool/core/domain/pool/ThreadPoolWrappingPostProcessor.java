package com.jovia.dynamic.threadpool.core.domain.pool;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @author Jay
 * @date 2025-11-15-11:20
 */
public class ThreadPoolWrappingPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if(bean instanceof ThreadPoolExecutor executor && !(bean instanceof AdaptiveThreadPoolExecutor)){
            return new AdaptiveThreadPoolExecutor(executor);
        }
        
        return BeanPostProcessor.super.postProcessBeforeInitialization(bean, beanName);
    }
}
