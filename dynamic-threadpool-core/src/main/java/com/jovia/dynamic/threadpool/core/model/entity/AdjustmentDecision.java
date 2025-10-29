package com.jovia.dynamic.threadpool.core.model.entity;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

/**
 * @author Jay
 * @date 2025-10-29-20:38
 */
@Getter
@Data
@Builder
public class AdjustmentDecision {
    public enum Type { NO_CHANGE, EXPAND_CORE, EXPAND_MAX, SHRINK_CORE }

    private final Type type;
    private final String reason;

    // 目标参数（可为空，用于由执行器计算或承载计算结果）
    private final Integer newCorePoolSize;
    private final Integer newMaximumPoolSize;
    private final Integer newQueueCapacity;

    // 常量：无需调整
    public static final AdjustmentDecision NO_CHANGE = new AdjustmentDecision(Type.NO_CHANGE, "", null, null, null);

    private AdjustmentDecision(Type type, String reason) {
        this.type = type;
        this.reason = reason;
        this.newCorePoolSize = null;
        this.newMaximumPoolSize = null;
        this.newQueueCapacity = null;
    }

    private AdjustmentDecision(Type type, String reason,
                               Integer newCorePoolSize,
                               Integer newMaximumPoolSize,
                               Integer newQueueCapacity) {
        this.type = type;
        this.reason = reason;
        this.newCorePoolSize = newCorePoolSize;
        this.newMaximumPoolSize = newMaximumPoolSize;
        this.newQueueCapacity = newQueueCapacity;
    }

    public static AdjustmentDecision noChange(String reason) {
        return new AdjustmentDecision(Type.NO_CHANGE, reason);
    }

    public static AdjustmentDecision expandCore(String reason) {
        return new AdjustmentDecision(Type.EXPAND_CORE, reason);
    }

    public static AdjustmentDecision expandMax(String reason) {
        return new AdjustmentDecision(Type.EXPAND_MAX, reason);
    }

    public static AdjustmentDecision shrinkCore(String reason) {
        return new AdjustmentDecision(Type.SHRINK_CORE, reason);
    }

    public static AdjustmentDecision of(Type type, int newCore, int newMax, int newQueue, String reason) {
        return new AdjustmentDecision(type, reason, newCore, newMax, newQueue);
    }

    public boolean shouldAdjust() {
        return this.type != Type.NO_CHANGE;
    }
}
