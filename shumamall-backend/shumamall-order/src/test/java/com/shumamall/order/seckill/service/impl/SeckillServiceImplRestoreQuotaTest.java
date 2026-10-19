package com.shumamall.order.seckill.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.order.api.ProductFeignClient;
import com.shumamall.order.seckill.constant.SeckillConstants;
import com.shumamall.order.seckill.dao.SeckillActivityMapper;
import com.shumamall.order.seckill.dao.SeckillOrderMapper;
import com.shumamall.order.seckill.entity.SeckillOrderEntity;
import com.shumamall.order.seckill.mq.SeckillMessageProducer;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 订单取消后按订单号回补秒杀额度。
 */
class SeckillServiceImplRestoreQuotaTest {

    private final SeckillOrderMapper seckillOrderMapper = mock(SeckillOrderMapper.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

    private final SeckillServiceImpl service = new SeckillServiceImpl(
            mock(SeckillActivityMapper.class),
            seckillOrderMapper,
            mock(ProductFeignClient.class),
            mock(SeckillMessageProducer.class),
            redisTemplate,
            new ObjectMapper());

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SeckillOrderEntity.class);
    }

    @Test
    void 已建单抢到状态后回补且不释放资格() {
        SeckillOrderEntity record = new SeckillOrderEntity();
        record.setId(7L);
        record.setActivityId(3L);
        record.setUserId(9L);
        record.setQuantity(1);
        record.setOrderNo("ORD001");
        record.setStatus(SeckillConstants.RECORD_CREATED);
        when(seckillOrderMapper.selectList(any())).thenReturn(List.of(record));
        when(seckillOrderMapper.update(any(), any())).thenReturn(1);
        when(redisTemplate.execute(any(), anyList(), any(), any())).thenReturn(4L);

        service.restoreQuotaForCancelledOrder("ORD001");

        verify(redisTemplate).execute(any(), anyList(), eq("1"), eq("0"));
    }

    @Test
    void 没有资格记录时不回补() {
        when(seckillOrderMapper.selectList(any())).thenReturn(List.of());

        service.restoreQuotaForCancelledOrder("ORD-NORMAL");

        verify(seckillOrderMapper, never()).update(any(), any());
        verify(redisTemplate, never()).execute(any(), anyList(), any(), any());
    }

    @Test
    void 资格状态已被改走时不回补() {
        SeckillOrderEntity record = new SeckillOrderEntity();
        record.setId(7L);
        record.setActivityId(3L);
        record.setUserId(9L);
        record.setQuantity(1);
        record.setStatus(SeckillConstants.RECORD_CREATED);
        when(seckillOrderMapper.selectList(any())).thenReturn(List.of(record));
        when(seckillOrderMapper.update(any(), any())).thenReturn(0);

        service.restoreQuotaForCancelledOrder("ORD001");

        verify(redisTemplate, never()).execute(any(), anyList(), any(), any());
    }
}
