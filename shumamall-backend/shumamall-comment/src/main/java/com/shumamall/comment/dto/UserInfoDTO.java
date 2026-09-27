package com.shumamall.comment.dto;

import lombok.Data;

/**
 * 用户信息 DTO（评论服务通过 Feign 从 user 服务获取用户名/头像快照）。
 */
@Data
public class UserInfoDTO {

    /** 用户 ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 头像 */
    private String avatar;
}
