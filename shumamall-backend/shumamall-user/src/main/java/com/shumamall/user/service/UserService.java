package com.shumamall.user.service;

import com.shumamall.user.dto.UserVO;

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
     * @param userId 用户 ID
     * @param phone  手机号（可选）
     * @param email  邮箱（可选）
     * @param nickname 昵称（可选）
     * @param gender 性别（可选）
     */
    void updateUserInfo(Long userId, String phone, String email, String nickname, Integer gender);

    /**
     * 用户总数（供管理端仪表盘聚合调用）。
     *
     * @return 用户总数
     */
    Long countUsers();
}
