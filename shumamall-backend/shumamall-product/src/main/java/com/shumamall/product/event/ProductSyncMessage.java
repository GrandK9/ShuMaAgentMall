package com.shumamall.product.event;

import lombok.Data;

/**
 * 商品变更同步消息（发布到 search 服务消费队列）。
 */
@Data
public class ProductSyncMessage {

    /**
     * 消息唯一标识（由生产者生成：product-sync-{productId}-{action}-{timestamp}）。
     * <p>
     * 两处用途：生产者 confirm/returns 回调按它定位具体消息；消费端以它做幂等去重，
     * 保证同一事件重复投递只被处理一次。
     */
    private String messageId;

    /** 商品 ID */
    private Long productId;

    /** 操作类型：UPSERT-新增/更新 DELETE-删除 */
    private String action;

    /** 事件时间戳 */
    private long timestamp;
}
