package com.shumamall.comment.dto;

import lombok.Data;

/**
 * 用户信息 DTO。评论列表按用户 ID 取当前昵称和头像时使用。
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
