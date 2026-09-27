package com.shumamall.agent.dto;

import lombok.Data;

/**
 * 商品变更同步消息（消费 product 服务发布的商品变更事件）。
 */
@Data
public class ProductSyncMessage {

    /**
     * 消息唯一标识（由生产者生成：product-sync-{productId}-{action}-{timestamp}）。
     * <p>
     * 消费端以它做幂等去重：同一 messageId 重复投递只处理一次。
     */
    private String messageId;

    /** 商品 ID */
    private Long productId;

    /** 操作类型：UPSERT-新增/更新 DELETE-删除 */
    private String action;

    /** 事件时间戳 */
    private long timestamp;
}
