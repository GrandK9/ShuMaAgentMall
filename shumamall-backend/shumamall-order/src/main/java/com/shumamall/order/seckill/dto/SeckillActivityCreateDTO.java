package com.shumamall.order.seckill.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动创建请求（管理端）。
 */
@Data
public class SeckillActivityCreateDTO {

    /** 活动名称 */
    @NotBlank(message = "活动名称不能为空")
    @Size(max = 100, message = "活动名称不能超过 100 个字符")
    private String name;

    /** 参与秒杀的 SKU ID */
    @NotNull(message = "SKU ID 不能为空")
    private Long skuId;

    /** 商品 ID */
    @NotNull(message = "商品 ID 不能为空")
    private Long productId;

    /** 秒杀价 */
    @NotNull(message = "秒杀价不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价必须大于 0")
    private BigDecimal seckillPrice;

    /** 活动库存总量 */
    @NotNull(message = "活动库存不能为空")
    @Min(value = 1, message = "活动库存至少为 1")
    private Integer totalStock;

    /** 每人限购数量，默认 1（当前实现按 1 处理，字段保留以支持后续扩展） */
    @Min(value = 1, message = "每人限购至少为 1")
    private Integer perUserLimit = 1;

    /** 开始时间 */
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    /** 结束时间 */
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
}
