package com.shumamall.user.service;

import com.shumamall.user.dto.UserBriefVO;
import com.shumamall.user.dto.UserVO;

import java.util.List;

/**
 * 用户服务接口。
 */
public interface UserService {

    /**
     * 根据用户 ID 查询用户信息（脱敏后返回）。
     */
    UserVO getUserInfo(Long userId);

    /**
     * 更新用户信息。
     *
     * @param userId   用户 ID
     * @param phone    手机号（可选）
     * @param email    邮箱（可选）
     * @param nickname 昵称（可选）
     * @param gender   性别（可选）
     */
    void updateUserInfo(Long userId, String phone, String email, String nickname, Integer gender);

    /**
     * 按用户 ID 批量取展示资料（昵称、登录名、头像），供评论列表填充。
     *
     * @param userIds 用户 ID，空列表返回空
     * @return 查到的用户，不含手机号和邮箱
     */
    List<UserBriefVO> listBrief(List<Long> userIds);

    /**
     * 用户总数（供管理端仪表盘聚合调用）。
     *
     * @return 用户总数
     */
    Long countUsers();
}
