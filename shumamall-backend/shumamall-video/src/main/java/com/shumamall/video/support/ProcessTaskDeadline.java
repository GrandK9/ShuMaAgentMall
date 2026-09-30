package com.shumamall.video.support;

import com.shumamall.video.exception.VideoValidationException;

import java.util.concurrent.TimeUnit;

/**
 * 单条视频处理任务的整段 deadline（ThreadLocal），在各生命周期节点调用 {@link #check()}。
 */
public final class ProcessTaskDeadline {

    private static final ThreadLocal<Long> DEADLINE_NANOS = new ThreadLocal<>();

    private ProcessTaskDeadline() {
    }

    /**
     * 在限定总时长内执行任务，结束后清理 ThreadLocal。
     */
    public static void runWithDeadline(long maxSeconds, Runnable action) {
        if (maxSeconds <= 0) {
            action.run();
            return;
        }
        DEADLINE_NANOS.set(System.nanoTime() + TimeUnit.SECONDS.toNanos(maxSeconds));
        try {
            action.run();
        } finally {
            DEADLINE_NANOS.remove();
        }
    }

    /**
     * 超过任务总 deadline 时抛出业务校验异常。
     */
    public static void check() {
        Long deadline = DEADLINE_NANOS.get();
        if (deadline != null && System.nanoTime() > deadline) {
            throw new VideoValidationException("视频处理总时长超限");
        }
    }
}
