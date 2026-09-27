package com.shumamall.permission.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 角色 DTO。
 */
@Data
public class RoleDTO {

    private Long id;

    /** 角色名称，如 "运营编辑" */
    @NotBlank(message = "角色名称不能为空")
    private String name;

    /** 角色编码，如 "role_editor" */
    @NotBlank(message = "角色编码不能为空")
    private String code;

    /** 状态：0-禁用 1-启用 */
    private Integer status;

    /** 关联的权限 ID 列表 */
    private List<Long> permissionIds;
}
