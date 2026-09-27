package com.shumamall.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 销售趋势项（按天聚合，管理端仪表盘折线图数据）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SalesTrendItemVO {

    /** 日期（yyyy-MM-dd） */
    private LocalDate date;

    /** 当天已支付订单数 */
    private Long orderCount;

    /** 当天销售额 */
    private BigDecimal salesAmount;
}
