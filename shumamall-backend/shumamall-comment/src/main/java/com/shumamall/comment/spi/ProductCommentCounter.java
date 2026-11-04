package com.shumamall.comment.spi;

/**
 * 商品评论数回写端口。合并进商品进程时由商品模块提供本地实现；独立部署时可由 Feign 适配。
 */
public interface ProductCommentCounter {

    /**
     * 调整商品冗余评论数。
     *
     * @param productId 商品 ID
     * @param delta     增量（发评/回复 +1，删除 -1）
     */
    void updateCommentCount(Long productId, int delta);
}
