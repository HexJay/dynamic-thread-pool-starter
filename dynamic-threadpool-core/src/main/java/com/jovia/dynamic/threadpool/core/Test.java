package com.jovia.dynamic.threadpool.core;

import com.jovia.dynamic.threadpool.core.domain.pool.ResizableBlockingQueue;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author Jay
 * @date 2025-10-29-10:57
 */
public class Test {
    public static void main(String[] args) {
        ThreadPoolExecutor threadPoolTaskExecutor = buildThreadPoolTaskExecutor();
        threadPoolTaskExecutor.execute(() -> sayHi("execute"));
        threadPoolTaskExecutor.submit(() -> sayHi("submit"));
        threadPoolTaskExecutor.setCorePoolSize(2);


    }   

    private static void sayHi(String name) {
        String printStr = "【thread-name:" + Thread.currentThread().getName() + ",执行方式:" + name + "】";
        System.out.println(printStr);
        throw new RuntimeException(printStr + ",我异常啦!哈哈哈!");
    }

    private static ThreadPoolExecutor buildThreadPoolTaskExecutor() {

        return new ThreadPoolExecutor(5, 10, 0L, TimeUnit.MILLISECONDS,new ResizableBlockingQueue<>(10));
    }
}