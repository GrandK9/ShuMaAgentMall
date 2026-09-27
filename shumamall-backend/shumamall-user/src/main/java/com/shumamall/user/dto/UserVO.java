package com.shumamall.user.dto;

import lombok.Data;

/**
 * 用户信息 VO（脱敏后返回前端）。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    /** 手机号，脱敏显示如 138****1234 */
    private String phone;

    /** 邮箱，脱敏显示如 t***@example.com */
    private String email;

    /** 性别 0-未知 1-男 2-女 */
    private Integer gender;

    /** 状态 0-禁用 1-正常 */
    private Integer status;
}
