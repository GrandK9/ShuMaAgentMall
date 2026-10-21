package com.shumamall.comment.service.impl;

import com.shumamall.comment.dto.UserInfoDTO;
import com.shumamall.comment.entity.CommentDocument;
import com.shumamall.comment.feign.ProductFeignClient;
import com.shumamall.comment.feign.UserFeignClient;
import com.shumamall.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论列表按用户 ID 取当前昵称和头像，不读文档里的旧展示名。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplProfileFillTest {

    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private ProductFeignClient productFeignClient;

    private CommentServiceImpl service;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CommentServiceImpl(mongoTemplate, stringRedisTemplate, userFeignClient, productFeignClient);
    }

    @Test
    void hotComments_用当前昵称和头像填充() {
        CommentDocument doc = new CommentDocument();
        doc.setId(1L);
        doc.setUserId(7L);
        doc.setLikeCount(3);
        when(mongoTemplate.find(any(Query.class), eq(CommentDocument.class))).thenReturn(List.of(doc));
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));

        UserInfoDTO user = new UserInfoDTO();
        user.setId(7L);
        user.setUsername("login");
        user.setNickname("新昵称");
        user.setAvatar("http://img/a.png");
        when(userFeignClient.listBrief(any())).thenReturn(R.ok(List.of(user)));

        var views = service.hotComments(10);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getUsername()).isEqualTo("新昵称");
        assertThat(views.get(0).getAvatar()).isEqualTo("http://img/a.png");
    }

    @Test
    void hotComments_用户服务不可用时降级为用户编号() {
        CommentDocument doc = new CommentDocument();
        doc.setId(1L);
        doc.setUserId(7L);
        doc.setLikeCount(3);
        when(mongoTemplate.find(any(Query.class), eq(CommentDocument.class))).thenReturn(List.of(doc));
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(userFeignClient.listBrief(any())).thenReturn(R.failed(com.shumamall.common.result.ResultCode.SERVICE_UNAVAILABLE));

        var views = service.hotComments(10);

        assertThat(views.get(0).getUsername()).isEqualTo("用户7");
        assertThat(views.get(0).getAvatar()).isNull();
    }

    @Test
    void hotComments_缓存命中时不再请求用户服务() {
        CommentDocument doc = new CommentDocument();
        doc.setId(1L);
        doc.setUserId(7L);
        doc.setLikeCount(3);
        when(mongoTemplate.find(any(Query.class), eq(CommentDocument.class))).thenReturn(List.of(doc));
        when(valueOperations.multiGet(anyList())).thenReturn(List.of(
                "{\"id\":7,\"username\":\"login\",\"nickname\":\"缓存昵称\",\"avatar\":\"http://img/c.png\"}"));

        var views = service.hotComments(10);

        assertThat(views.get(0).getUsername()).isEqualTo("缓存昵称");
        assertThat(views.get(0).getAvatar()).isEqualTo("http://img/c.png");
        verify(userFeignClient, never()).listBrief(any());
    }
}
