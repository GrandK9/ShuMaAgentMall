package com.shumamall.user.service.impl;

import com.shumamall.common.cache.UserBriefCache;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.user.dao.UserMapper;
import com.shumamall.user.dto.UserBriefVO;
import com.shumamall.user.dto.UserVO;
import com.shumamall.user.entity.UserEntity;
import com.shumamall.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 用户服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

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
        boolean nicknameChanged = false;
        if (phone != null) {
            user.setPhone(phone);
            changed = true;
        }
        if (email != null) {
            user.setEmail(email);
            changed = true;
        }
        if (nickname != null && !Objects.equals(nickname, user.getNickname())) {
            user.setNickname(nickname);
            changed = true;
            nicknameChanged = true;
        }
        if (gender != null) {
            user.setGender(gender);
            changed = true;
        }

        if (changed) {
            userMapper.updateById(user);
            log.info("用户信息更新: userId={}", userId);
        }
        if (nicknameChanged) {
            evictBriefCache(userId);
        }
    }

    /** 评论列表按这个键读当前昵称和头像。删掉后下一次展示会重新向用户表取值。 */
    private void evictBriefCache(Long userId) {
        try {
            stringRedisTemplate.delete(UserBriefCache.key(userId));
        } catch (Exception e) {
            log.warn("删除用户展示缓存失败: userId={}, err={}", userId, e.getMessage());
        }
    }

    @Override
    public List<UserBriefVO> listBrief(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = userIds.stream().filter(id -> id != null).distinct().limit(200).toList();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(ids).stream().map(user -> {
            UserBriefVO brief = new UserBriefVO();
            brief.setId(user.getId());
            brief.setUsername(user.getUsername());
            brief.setNickname(user.getNickname());
            brief.setAvatar(user.getAvatar());
            return brief;
        }).toList();
    }

    @Override
    public Long countUsers() {
        return userMapper.selectCount(null);
    }
}
