package com.shumamall.comment.feign;

import com.shumamall.common.result.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 商品服务 Feign 客户端。
 * <p>
 * 评论服务在发表评论/回复时回写商品评论数（+1），删除评论时回写（-1）。
 * 内部接口路径 {@code /api/v1/internal/product} 不对外暴露。
 * <p>
 * 商品服务不可用（超时/报错/熔断打开）时由 {@link ProductFeignFallbackFactory} 记录警告并跳过回写，
 * 该字段是展示用的冗余统计，不影响发表评论主流程。
 */
@FeignClient(name = "shumamall-product", path = "/api/v1/internal/product",
        fallbackFactory = ProductFeignFallbackFactory.class)
public interface ProductFeignClient {

    /**
     * 调整商品评论数。
     *
     * @param productId 商品 ID
     * @param delta     增量（发评 +1，删除 -1）
     * @return 操作结果
     */
    @PutMapping("/{productId}/comment-count")
    R<Void> updateCommentCount(@PathVariable("productId") Long productId,
                               @RequestParam("delta") Integer delta);
}
