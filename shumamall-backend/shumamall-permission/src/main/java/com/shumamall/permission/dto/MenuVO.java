package com.shumamall.permission.dto;

import lombok.Data;

import java.util.List;

/**
 * 菜单树节点 VO。
 * <p>
 * 返回给前端用于渲染动态菜单树。
 */
@Data
public class MenuVO {

    /** 权限点 ID */
    private Long id;

    /** 权限编码 */
    private String code;

    /** 菜单名称 */
    private String name;

    /** 前端路由路径 */
    private String path;

    /** 子菜单 */
    private List<MenuVO> children;
}
