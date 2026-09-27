package com.shumamall.dict.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统字典实体（码表）。
 * <p>
 * 对应表 {@code sys_dict}：type 分组 + code 编码，parentCode 构建树形结构。
 */
@Data
@TableName("sys_dict")
public class DictEntity {

    /** 雪花 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 字典类型编码，如 product_spec、payment_method */
    private String type;

    /** 字典类型中文名，如 "商品规格" */
    private String typeLabel;

    /** 字典项编码，如 color_black */
    private String code;

    /** 字典项显示值，如 "黑色" */
    private String label;

    /** 字典项实际值（可选），如 hex #000000 */
    private String value;

    /** 父级编码（构建树形结构） */
    private String parentCode;

    /** 排序 */
    private Integer sortOrder;

    /** 状态 0-禁用 1-启用 */
    private Integer status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
