package com.shumamall.dict.dto;

import lombok.Data;

import java.util.List;

/**
 * 字典项视图对象（用户端查询/树结构返回）。
 */
@Data
public class DictVO {

    /** 字典项编码 */
    private String code;

    /** 字典项显示值 */
    private String label;

    /** 字典项实际值（可选） */
    private String value;

    /** 子项（树结构时非空） */
    private List<DictVO> children;
}
