package com.jovia.dynamic.threadpool.registry.redis.registry;

import com.alibaba.fastjson.JSON;
import com.jovia.dynamic.threadpool.registry.redis.constant.RedisKeys;
import com.jovia.dynamic.threadpool.api.IConfigCenter;
import com.jovia.dynamic.threadpool.api.IConfigChangeListener;
import com.jovia.dynamic.threadpool.api.IRegistry;
import com.jovia.dynamic.threadpool.core.model.entity.ThreadPoolConfig;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.api.options.KeysScanOptions;
import org.springframework.util.CollectionUtils;

import java.util.*;

/**
 * redis 注册中心
 *
 * @author Jay
 * @date 2025-10-19-16:13
 */
public class RedisConfigCenter implements IRegistry, IConfigCenter {

    private final RedissonClient redisson;

    public RedisConfigCenter(RedissonClient redisson) {
        this.redisson = redisson;
    }

    @Override
    public void reportAllThreadPools(List<ThreadPoolConfig> threadPoolEntities) {
        if (CollectionUtils.isEmpty(threadPoolEntities)) {
            return;
        }

        String appName = threadPoolEntities.get(0).getAppName();
        RMap<String, String> appMap = redisson.getMap(RedisKeys.THREAD_POOL_CONFIGS + appName);
        
        Map<String, String> map = new HashMap<>();
        for (ThreadPoolConfig entity : threadPoolEntities) {
            map.put(entity.getThreadPoolName(), JSON.toJSONString(entity));
        }
        appMap.putAll(map);
    }

    @Override
    public void reportThreadPoolConfig(ThreadPoolConfig entity) {
        if (entity == null) return;
        String appName = entity.getAppName();
        RMap<String, String> appMap = redisson.getMap(RedisKeys.THREAD_POOL_CONFIGS + appName);

        appMap.put(entity.getThreadPoolName(), JSON.toJSONString(entity));
    }

    @Override
    public void subscribeConfigChange(String appName, IConfigChangeListener listener) {
        RTopic topic = redisson.getTopic(RedisKeys.THREAD_POOL_CONFIG_TOPIC + appName);
        topic.addListener(String.class, (channel, msg) -> {
            ThreadPoolConfig entity = JSON.parseObject(msg, ThreadPoolConfig.class);
            listener.onConfigChange(entity);
        });
    }

    @Override
    public List<ThreadPoolConfig> fetchThreadPoolConfigs(String appName) {
        
        // 获取所有 THREAD_POOL_CONFIGS:* 的 key
        KeysScanOptions options = KeysScanOptions.defaults().pattern(RedisKeys.THREAD_POOL_CONFIGS + "*");
        Iterable<String> keys = redisson.getKeys().getKeys(options);

        List<ThreadPoolConfig> allThreadPools = new ArrayList<>();

        for (String key : keys) {
            RMap<String, String> appMap = redisson.getMap(key);
            Collection<String> values = appMap.values();

            values.stream()
                    .map(json -> JSON.parseObject(json, ThreadPoolConfig.class))
                    .forEach(allThreadPools::add);
        }
        
        return allThreadPools;
    }
}
