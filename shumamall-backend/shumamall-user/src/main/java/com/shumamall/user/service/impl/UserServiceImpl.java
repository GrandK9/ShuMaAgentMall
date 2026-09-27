package com.shumamall.user.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.user.dao.UserMapper;
import com.shumamall.user.dto.UserVO;
import com.shumamall.user.entity.UserEntity;
import com.shumamall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Override
    public UserVO getUserInfo(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);

        // 脱敏手机号：138****1234
        if (vo.getPhone() != null && vo.getPhone().length() == 11) {
            vo.setPhone(vo.getPhone().replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
        } else if (vo.getPhone() != null && vo.getPhone().length() > 7) {
            vo.setPhone(vo.getPhone().replaceAll("(\\d{3})\\d+(\\d{4})", "$1****$2"));
        }

        // 脱敏邮箱：t***@example.com
        if (vo.getEmail() != null && vo.getEmail().contains("@")) {
            String prefix = vo.getEmail().split("@")[0];
            String domain = vo.getEmail().split("@")[1];
            if (prefix.length() > 1) {
                vo.setEmail(prefix.charAt(0) + "***@" + domain);
            } else {
                vo.setEmail(prefix + "***@" + domain);
            }
        }

        // 密码不返回
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(Long userId, String phone, String email, String nickname, Integer gender) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }

        boolean changed = false;
        if (phone != null) {
            user.setPhone(phone);
            changed = true;
        }
        if (email != null) {
            user.setEmail(email);
            changed = true;
        }
        if (nickname != null) {
            user.setNickname(nickname);
            changed = true;
        }
        if (gender != null) {
            user.setGender(gender);
            changed = true;
        }

        if (changed) {
            userMapper.updateById(user);
            log.info("用户信息更新: userId={}", userId);
        }
    }

    @Override
    public Long countUsers() {
        return userMapper.selectCount(null);
    }
}
