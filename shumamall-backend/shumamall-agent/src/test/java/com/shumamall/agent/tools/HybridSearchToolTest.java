package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.agent.search.service.ProductHybridSearchService;
import com.shumamall.agent.search.service.SearchFallbackService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ToolContext;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 混合检索工具单测（mock 向量化与检索服务，不依赖真实 ES/LLM）。
 * <p>
 * 验证目标：
 * <ol>
 *   <li>工具把用户 query 文本向量化（走 embedQuery，检索侧带指令前缀）</li>
 *   <li>HybridSearchRequest 参数正确（keyword / queryVector / topK / numCandidates / size）</li>
 *   <li>返回结果裁剪为精简字段 JSON，不含 embedding</li>
 *   <li>异常时返回错误 JSON 不抛出</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class HybridSearchToolTest {

    @Mock
    private ProductHybridSearchService hybridSearchService;

    @Mock
    private ProductEmbeddingService embeddingService;

    @Mock
    private SearchFallbackService fallbackService;

    private HybridSearchTool tool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tool = new HybridSearchTool(hybridSearchService, embeddingService, fallbackService, objectMapper);
    }

    @Test
    void hybridSearchProduct_正常检索并裁剪字段() throws Exception {
        when(embeddingService.embedQuery("2000元内拍照好的手机")).thenReturn(List.of(0.1f, 0.2f, 0.3f));
        ProductDoc doc = new ProductDoc();
        doc.setId(1L);
        doc.setName("小米14");
        doc.setSubtitle("徕卡影像");
        doc.setBrand("小米");
        doc.setCategory("手机");
        doc.setPrice(new BigDecimal("3999"));
        doc.setStock(50);
        doc.setSalesVolume(5000);
        doc.setEmbedding(List.of(9.9f)); // 应被裁剪掉
        when(hybridSearchService.search(any(HybridSearchRequest.class))).thenReturn(List.of(doc));

        String json = tool.hybridSearchProduct("2000元内拍照好的手机", 5, new ToolContext(Map.of("userId", 1L)));

        assertThat(json).contains("小米14").contains("徕卡影像").doesNotContain("embedding");

        // 验证请求构造
        ArgumentCaptor<HybridSearchRequest> captor = ArgumentCaptor.forClass(HybridSearchRequest.class);
        verify(hybridSearchService).search(captor.capture());
        HybridSearchRequest req = captor.getValue();
        assertThat(req.getKeyword()).isEqualTo("2000元内拍照好的手机");
        assertThat(req.getQueryVector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(req.getTopK()).isEqualTo(5);
        assertThat(req.getNumCandidates()).isEqualTo(25);
        assertThat(req.getSize()).isEqualTo(5);
    }

    @Test
    void hybridSearchProduct_topK为空时_默认5且最多20() throws Exception {
        when(embeddingService.embedQuery(any())).thenReturn(List.of(0.1f));
        when(hybridSearchService.search(any())).thenReturn(List.of());

        tool.hybridSearchProduct("耳机", null, new ToolContext(Map.of()));

        ArgumentCaptor<HybridSearchRequest> captor = ArgumentCaptor.forClass(HybridSearchRequest.class);
        verify(hybridSearchService).search(captor.capture());
        assertThat(captor.getValue().getSize()).isEqualTo(5);

        tool.hybridSearchProduct("耳机", 100, new ToolContext(Map.of()));
        verify(hybridSearchService, org.mockito.Mockito.atLeast(2)).search(captor.capture());
        List<HybridSearchRequest> requests = captor.getAllValues();
        assertThat(requests.get(requests.size() - 1).getSize()).isEqualTo(20);
    }

    @Test
    void hybridSearchProduct_检索服务异常时_返回错误JSON() throws Exception {
        when(embeddingService.embedQuery(any())).thenThrow(new RuntimeException("embedding failed"));

        String json = tool.hybridSearchProduct("手机", 5, new ToolContext(Map.of()));

        assertThat(json).contains("error").contains("embedding failed");
    }

    @Test
    void hybridSearchProduct_检索为空时_触发兜底热销推荐() throws Exception {
        when(embeddingService.embedQuery("火星手机")).thenReturn(List.of(0.1f));
        when(hybridSearchService.search(any())).thenReturn(List.of());
        ProductItemDTO hot = new ProductItemDTO();
        hot.setId(100L);
        hot.setName("热销手机");
        hot.setPrice(new BigDecimal("2999"));
        when(fallbackService.hotProducts(5)).thenReturn(List.of(hot));

        String json = tool.hybridSearchProduct("火星手机", 5, new ToolContext(Map.of()));

        assertThat(json).contains("fallback").contains("热销手机").contains("未找到精确匹配的商品");
    }
}
