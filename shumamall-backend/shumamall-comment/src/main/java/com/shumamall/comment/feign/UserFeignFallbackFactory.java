package com.shumamall.comment.feign;

import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link UserFeignClient} 的降级工厂。
 * <p>
 * 用户服务超时、抛错或熔断打开时，返回 {@code code=5030} 的失败结果（data 为 null），
 * 由调用方 {@code CommentServiceImpl#fillUserSnapshot} 落到默认展示名「用户{userId}」。
 * <p>
 * 用 FallbackFactory 而不是 Fallback：能拿到具体异常 cause 并记入日志，
 * 降级原因可直接在日志里定位（超时 / 连接被拒 / 熔断开路）。
 */
@Slf4j
@Component
public class UserFeignFallbackFactory implements FallbackFactory<UserFeignClient> {

    @Override
    public UserFeignClient create(Throwable cause) {
        return userId -> {
            log.warn("用户服务调用失败，评论用户名降级为默认值: userId={}, cause={}", userId, cause.toString());
            return R.failed(ResultCode.SERVICE_UNAVAILABLE, "用户服务暂不可用");
        };
    }
}
