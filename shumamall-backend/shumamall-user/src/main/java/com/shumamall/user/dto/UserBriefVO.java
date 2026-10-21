package com.shumamall.user.dto;

import lombok.Data;

/**
 * 用户展示资料。只含评论列表需要的字段。
 */
@Data
public class UserBriefVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;
}
