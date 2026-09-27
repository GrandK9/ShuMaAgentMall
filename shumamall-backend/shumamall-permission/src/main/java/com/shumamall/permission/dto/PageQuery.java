package com.shumamall.permission.dto;

import lombok.Data;

/**
 * 分页查询参数。
 */
@Data
public class PageQuery {

    /** 当前页码 */
    private Integer page = 1;

    /** 每页条数 */
    private Integer size = 20;
}
