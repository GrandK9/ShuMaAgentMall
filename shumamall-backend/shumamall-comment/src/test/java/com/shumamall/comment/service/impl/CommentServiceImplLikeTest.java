package com.shumamall.comment.service.impl;

import com.mongodb.client.result.DeleteResult;
import com.shumamall.comment.entity.CommentDocument;
import com.shumamall.comment.entity.CommentLikeDocument;
import com.shumamall.comment.entity.CommentReportDocument;
import com.shumamall.comment.feign.ProductFeignClient;
import com.shumamall.comment.feign.UserFeignClient;
import com.shumamall.common.auth.SecurityContext;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 点赞和举报以关系集合的唯一索引决定计数，Redis 只挡短时间连点。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceImplLikeTest {

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
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new CommentServiceImpl(mongoTemplate, stringRedisTemplate, userFeignClient, productFeignClient);
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    @Test
    void like_首次插入后计数加一() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(3));
        when(valueOperations.get("comment:like:10:7")).thenReturn(null);
        when(mongoTemplate.insert(any(CommentLikeDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class)))
                .thenReturn(visible(4));

        assertThat(service.like(7L, 10L)).isEqualTo(4);
        verify(valueOperations).set(eq("comment:like:10:7"), eq("1"), any(Duration.class));
    }

    @Test
    void like_唯一索引冲突时计数不变() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(3));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(mongoTemplate.insert(any(CommentLikeDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        assertThat(service.like(7L, 10L)).isEqualTo(3);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class));
    }

    @Test
    void like_短缓存命中时不再写关系() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(3));
        when(valueOperations.get("comment:like:10:7")).thenReturn("1");

        assertThat(service.like(7L, 10L)).isEqualTo(3);
        verify(mongoTemplate, never()).insert(any(CommentLikeDocument.class));
    }

    @Test
    void unlike_删掉关系后计数减一() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(3));
        when(mongoTemplate.remove(any(Query.class), eq(CommentLikeDocument.class)))
                .thenReturn(DeleteResult.acknowledged(1));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class)))
                .thenReturn(visible(2));

        assertThat(service.unlike(7L, 10L)).isEqualTo(2);
        verify(stringRedisTemplate).delete("comment:like:10:7");
    }

    @Test
    void unlike_没有关系时计数不变() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(3));
        when(mongoTemplate.remove(any(Query.class), eq(CommentLikeDocument.class)))
                .thenReturn(DeleteResult.acknowledged(0));

        assertThat(service.unlike(7L, 10L)).isEqualTo(3);
        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class));
    }

    @Test
    void report_首次插入后计数加一_满十次隐藏() {
        CommentDocument doc = visible(0);
        doc.setReportCount(9);
        CommentDocument updated = visible(0);
        updated.setReportCount(10);
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(doc);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(mongoTemplate.insert(any(CommentReportDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class)))
                .thenReturn(updated);

        service.report(7L, 10L);

        verify(mongoTemplate).updateFirst(any(Query.class), any(Update.class), eq(CommentDocument.class));
        verify(valueOperations).set(eq("comment:report:10:7"), eq("1"), any(Duration.class));
    }

    @Test
    void report_唯一索引冲突时不加计数() {
        when(mongoTemplate.findById(10L, CommentDocument.class)).thenReturn(visible(0));
        when(valueOperations.get(anyString())).thenReturn(null);
        when(mongoTemplate.insert(any(CommentReportDocument.class))).thenThrow(new DuplicateKeyException("dup"));

        service.report(7L, 10L);

        verify(mongoTemplate, never()).findAndModify(any(Query.class), any(Update.class), any(), eq(CommentDocument.class));
        verify(mongoTemplate, never()).updateFirst(any(Query.class), any(Update.class), eq(CommentDocument.class));
    }

    @Test
    void like_未登录拒绝() {
        assertThatThrownBy(() -> service.like(null, 10L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void hotComments_标出当前用户已赞的评论() {
        CommentDocument doc = visible(3);
        doc.setUserId(8L);
        CommentLikeDocument like = new CommentLikeDocument();
        like.setCommentId(10L);
        SecurityContext.setUserId(7L);
        when(mongoTemplate.find(any(Query.class), eq(CommentDocument.class))).thenReturn(List.of(doc));
        when(mongoTemplate.find(any(Query.class), eq(CommentLikeDocument.class))).thenReturn(List.of(like));
        when(valueOperations.multiGet(anyList())).thenReturn(Collections.singletonList(null));
        when(userFeignClient.listBrief(any())).thenReturn(R.ok(List.of()));

        var views = service.hotComments(10);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).getLiked()).isTrue();
    }

    private CommentDocument visible(int likeCount) {
        CommentDocument doc = new CommentDocument();
        doc.setId(10L);
        doc.setStatus(0);
        doc.setLikeCount(likeCount);
        doc.setReportCount(0);
        return doc;
    }
}
