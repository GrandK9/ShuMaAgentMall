package com.shumamall.order.seckill.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动实体。
 * <p>
 * 库存以本表的 {@code totalStock} 为准（活动维度），与 SKU 自身的库存相互独立：
 * 秒杀库存是"从 SKU 库存里划出来的一块额度"，预热时取两者较小值写入 Redis，
 * 扣减仍然走 SKU 的条件更新，因此活动库存被超卖时数据库层面依然拦得住。
 */
@Data
@TableName("seckill_activity")
public class SeckillActivityEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动名称 */
    private String name;

    /** 参与秒杀的 SKU ID */
    private Long skuId;

    /** 商品 ID（冗余，便于列表页直接展示商品） */
    private Long productId;

    /** 秒杀价 */
    private BigDecimal seckillPrice;

    /** 活动库存总量 */
    private Integer totalStock;

    /** 每人限购数量（当前实现按 1 处理，字段保留以支持后续扩展） */
    private Integer perUserLimit;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 状态 0-未上线 1-已上线 2-已结束 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
