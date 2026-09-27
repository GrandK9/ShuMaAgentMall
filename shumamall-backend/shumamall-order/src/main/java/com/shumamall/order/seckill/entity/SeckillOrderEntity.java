package com.shumamall.order.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 秒杀资格记录（本地消息表）。
 * <p>
 * 这张表承担三个职责，缺一不可：
 * <ol>
 *   <li><b>本地消息表</b>：预扣成功后先落库再发 MQ。只发消息不落库的话，MQ 丢消息时
 *       Redis 库存白扣、用户永远等不到订单，且没有任何线索可以复盘；</li>
 *   <li><b>对账依据</b>：长时间停留在 {@code RECORD_QUEUED} 说明消息可能丢失，可重投；
 *       关联订单已取消则需回补 Redis 库存；</li>
 *   <li><b>一人一单的数据库兜底</b>：{@code uk_activity_user} 唯一索引保证即使 Redis 全挂、
 *       资格占位丢失，同一用户也无法在同一活动重复下单。</li>
 * </ol>
 */
@Data
@TableName("seckill_order")
public class SeckillOrderEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求唯一标识（客户端一次抢购一个），既是 MQ 消息去重键也是消费幂等键 */
    private String requestId;

    /** 活动 ID */
    private Long activityId;

    /** 用户 ID */
    private Long userId;

    /** SKU ID（冗余自活动，避免对账时回查活动表） */
    private Long skuId;

    /** 购买数量 */
    private Integer quantity;

    /** 成交价（活动价快照，防止活动改价后对账失真） */
    private java.math.BigDecimal seckillPrice;

    /**
     * 收货地址 ID。
     * <p>
     * 必须落库：异步建单的消息只是"第一次尝试"，消息丢失时由对账任务按本表重投，
     * 那时早已没有请求上下文，只能靠这里存的建单参数还原消息。
     */
    private Long addressId;

    /** 订单备注（同上，属于重建消息所需参数） */
    private String remark;

    /** 建单成功后的订单号（未建单时为空） */
    private String orderNo;

    /** 状态 0-待建单 1-已建单 2-建单失败已回补 3-订单已取消已回补 4-建单中 */
    private Integer status;

    /** 失败原因（便于排查与告警） */
    private String failReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
