package com.shumamall.user.service.impl;

import com.shumamall.common.cache.UserBriefCache;
import com.shumamall.user.dao.UserMapper;
import com.shumamall.user.entity.UserEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 昵称变化时删除展示缓存，其它资料变更不动这个键。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplBriefCacheTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper, stringRedisTemplate);
    }

    @Test
    void updateUserInfo_昵称变化时删除展示缓存() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setNickname("旧昵称");
        when(userMapper.selectById(7L)).thenReturn(user);

        service.updateUserInfo(7L, null, null, "新昵称", null);

        verify(stringRedisTemplate).delete(UserBriefCache.key(7L));
    }

    @Test
    void updateUserInfo_只改手机号不删除展示缓存() {
        UserEntity user = new UserEntity();
        user.setId(7L);
        user.setNickname("旧昵称");
        when(userMapper.selectById(7L)).thenReturn(user);

        service.updateUserInfo(7L, "13800000000", null, null, null);

        verify(stringRedisTemplate, never()).delete(UserBriefCache.key(7L));
    }
}
