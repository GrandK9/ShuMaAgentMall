package com.shumamall.comment.feign;

import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/**
 * {@link ProductFeignClient} 的降级工厂。
 * <p>
 * 商品评论数是展示用的冗余统计字段，回写失败不影响发表评论主流程，
 * 因此降级为记录警告日志并跳过本次回写（返回 {@code code=5030}，与业务含义一致）。
 */
@Slf4j
@Component
public class ProductFeignFallbackFactory implements FallbackFactory<ProductFeignClient> {

    @Override
    public ProductFeignClient create(Throwable cause) {
        return (productId, delta) -> {
            log.warn("商品服务调用失败，跳过商品评论数回写: productId={}, delta={}, cause={}",
                    productId, delta, cause.toString());
            return R.failed(ResultCode.SERVICE_UNAVAILABLE, "商品服务暂不可用");
        };
    }
}
