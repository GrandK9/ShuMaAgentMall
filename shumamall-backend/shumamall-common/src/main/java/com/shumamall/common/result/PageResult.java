package com.shumamall.common.result;

import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页返回结果。
 *
 * @param <T> 数据类型
 */
@Data
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页码 */
    private long page;
    /** 每页条数 */
    private long size;
    /** 总记录数 */
    private long total;
    /** 总页数 */
    private long pages;
    /** 数据列表 */
    private List<T> records;

    public PageResult() {
        this.records = Collections.emptyList();
    }

    public PageResult(long page, long size, long total, List<T> records) {
        this.page = page;
        this.size = size;
        this.total = total;
        this.pages = size == 0 ? 0 : (total + size - 1) / size;
        this.records = records == null ? Collections.emptyList() : records;
    }
}
