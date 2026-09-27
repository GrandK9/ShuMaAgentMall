package com.shumamall.agent.search.service;

import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 检索兜底服务单测。
 */
@ExtendWith(MockitoExtension.class)
class SearchFallbackServiceTest {

    @Mock
    private ProductFeignClient productFeignClient;

    private SearchFallbackService fallbackService;

    @BeforeEach
    void setUp() {
        fallbackService = new SearchFallbackService(productFeignClient);
    }

    private ProductItemDTO item(Long id, String name) {
        ProductItemDTO dto = new ProductItemDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setPrice(new BigDecimal("1999"));
        return dto;
    }

    @Test
    void hotProducts_查询热销商品并按销量倒序() {
        when(productFeignClient.list(any(), eq(5), any(), any(), any(), any(), eq(1), any(), any(), eq("sales_volume"), eq("desc")))
                .thenReturn(R.ok(new PageResult<>(1, 5, 2, List.of(item(1L, "爆款A"), item(2L, "爆款B")))));

        List<ProductItemDTO> result = fallbackService.hotProducts(5);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("爆款A");

        // 校验分页参数
        ArgumentCaptor<Integer> pageCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Integer> hotCaptor = ArgumentCaptor.forClass(Integer.class);
        org.mockito.Mockito.verify(productFeignClient).list(pageCaptor.capture(), sizeCaptor.capture(),
                any(), any(), any(), any(), hotCaptor.capture(), any(), any(), eq("sales_volume"), eq("desc"));
        assertThat(pageCaptor.getValue()).isEqualTo(1);
        assertThat(sizeCaptor.getValue()).isEqualTo(5);
        assertThat(hotCaptor.getValue()).isEqualTo(1);
    }

    @Test
    void hotProducts_接口失败时_返回空列表() {
        when(productFeignClient.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.failed(500, "服务异常"));

        List<ProductItemDTO> result = fallbackService.hotProducts(5);

        assertThat(result).isEmpty();
    }

    @Test
    void hotProducts_异常时_返回空列表不抛出() {
        when(productFeignClient.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        List<ProductItemDTO> result = fallbackService.hotProducts(5);

        assertThat(result).isEmpty();
    }

    @Test
    void needFallback_空或null需要兜底() {
        assertThat(fallbackService.needFallback(null)).isTrue();
        assertThat(fallbackService.needFallback(List.of())).isTrue();
        assertThat(fallbackService.needFallback(List.of(item(1L, "x")))).isFalse();
    }
}
