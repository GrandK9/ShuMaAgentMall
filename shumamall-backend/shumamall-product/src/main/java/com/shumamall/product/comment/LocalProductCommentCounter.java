package com.shumamall.product.comment;

import com.shumamall.comment.spi.ProductCommentCounter;
import com.shumamall.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 评论数回写：与商品库同进程，直接调领域服务，避免 Feign 自调用。
 */
@Component
@RequiredArgsConstructor
public class LocalProductCommentCounter implements ProductCommentCounter {

    private final ProductService productService;

    @Override
    public void updateCommentCount(Long productId, int delta) {
        productService.updateCommentCount(productId, delta);
    }
}
