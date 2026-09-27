package com.shumamall.order.dto;

import lombok.Data;

/**
 * 订单分页查询参数。
 */
@Data
public class OrderPageQuery {

    /** 当前页码 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;

    /** 订单状态筛选 */
    private Integer status;

    /** 用户ID（管理端传入） */
    private Long userId;
}
