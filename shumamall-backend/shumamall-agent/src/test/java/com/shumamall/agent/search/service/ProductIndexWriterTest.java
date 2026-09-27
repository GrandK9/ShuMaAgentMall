package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品向量化灌库单测（mock Feign + ES 客户端，不依赖真实环境）。
 * <p>
 * 验证目标：
 * <ol>
 *   <li>分页拉取全部在售商品并逐条生成语义向量</li>
 *   <li>bulk 请求按商品 ID 写入正确索引</li>
 *   <li>mock 模式下向量为确定性伪向量（同文本同向量）</li>
 *   <li>无数据 / ES 写入异常时安全降级不抛出</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductIndexWriterTest {

    @Mock
    private ProductFeignClient productFeignClient;

    @Mock
    private ElasticsearchClient esClient;

    private ProductEmbeddingService embeddingService;
    private ProductIndexWriter writer;

    @BeforeEach
    void setUp() {
        // mock-llm=true：Embedding 服务退化为确定性伪向量，单测无需真实模型
        embeddingService = new ProductEmbeddingService(null, true, 4, "");
        writer = new ProductIndexWriter(productFeignClient, embeddingService, esClient, 100);
    }

    private ProductItemDTO item(long id, String name) {
        ProductItemDTO dto = new ProductItemDTO();
        dto.setId(id);
        dto.setName(name);
        dto.setSubtitle("官方正品");
        dto.setPrice(new BigDecimal("1999"));
        dto.setStock(100);
        dto.setSalesVolume(1000);
        return dto;
    }

    @Test
    void indexAllProducts_分页拉取并批量写入() throws Exception {
        // 全量 3 条可在默认 pageSize 100 下一页拉完
        when(productFeignClient.list(eq(1), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.ok(new PageResult<>(1, 100, 3, List.of(item(1, "小米14"), item(2, "iPhone15"), item(3, "华为Mate60")))));

        BulkResponse resp = mock(BulkResponse.class);
        when(resp.errors()).thenReturn(false);
        when(resp.items()).thenReturn(mockItems(3));
        when(esClient.bulk(any(BulkRequest.class))).thenReturn(resp);

        int written = writer.indexAllProducts();

        assertThat(written).isEqualTo(3);

        // 捕获全部 bulk 请求，断言索引名与文档 ID
        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(esClient, org.mockito.Mockito.atLeastOnce()).bulk(captor.capture());
        List<BulkOperation> ops = captor.getAllValues().stream()
                .flatMap(req -> req.operations().stream())
                .toList();
        assertThat(ops).hasSize(3);
        assertThat(ops).allSatisfy(op -> {
            assertThat(op.index().index()).isEqualTo(ProductIndexService.PRODUCT_INDEX);
            assertThat(op.index().document()).isNotNull();
        });
        List<String> ids = ops.stream().map(op -> op.index().id()).sorted().toList();
        assertThat(ids).containsExactly("1", "2", "3");
    }

    @Test
    void indexAllProducts_数据量超batchSize时_分批bulk写入() throws Exception {
        // batchSize=2，3 条数据应拆成 2+1 两个 bulk 请求
        writer = new ProductIndexWriter(productFeignClient, embeddingService, esClient, 2);
        when(productFeignClient.list(eq(1), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.ok(new PageResult<>(1, 100, 3, List.of(item(1, "小米14"), item(2, "iPhone15"), item(3, "华为Mate60")))));

        BulkResponse respFirst = mock(BulkResponse.class);
        when(respFirst.errors()).thenReturn(false);
        when(respFirst.items()).thenReturn(mockItems(2));
        BulkResponse respSecond = mock(BulkResponse.class);
        when(respSecond.errors()).thenReturn(false);
        when(respSecond.items()).thenReturn(mockItems(1));
        when(esClient.bulk(any(BulkRequest.class))).thenReturn(respFirst, respSecond);

        int written = writer.indexAllProducts();

        assertThat(written).isEqualTo(3);
        verify(esClient, org.mockito.Mockito.times(2)).bulk(any(BulkRequest.class));
    }

    private List<co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem> mockItems(int n) {
        List<co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(mock(co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem.class));
        }
        return items;
    }

    @Test
    void indexAllProducts_向量为确定性伪向量_同文本同向量() {
        List<Float> v1 = embeddingService.embed("小米14 官方正品");
        List<Float> v2 = embeddingService.embed("小米14 官方正品");
        assertThat(v1).hasSize(4).isEqualTo(v2);
        assertThat(embeddingService.embed("iPhone15 官方正品")).isNotEqualTo(v1);
    }

    @Test
    void indexAllProducts_无数据时_返回0且不写ES() throws Exception {
        when(productFeignClient.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.ok(new PageResult<>(1, 100, 0, List.of())));

        int written = writer.indexAllProducts();

        assertThat(written).isZero();
        verify(esClient, org.mockito.Mockito.never()).bulk(any(BulkRequest.class));
    }

    @Test
    void indexAllProducts_ES写入异常时_该批返回0不抛出() throws Exception {
        when(productFeignClient.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.ok(new PageResult<>(1, 100, 2, List.of(item(1, "小米14"), item(2, "iPhone15")))));
        when(esClient.bulk(any(BulkRequest.class))).thenThrow(new RuntimeException("connection refused"));

        int written = writer.indexAllProducts();

        assertThat(written).isZero();
    }

    @Test
    void indexAllProducts_接口返回失败时_终止灌库() throws Exception {
        when(productFeignClient.list(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(R.failed(500, "服务异常"));

        int written = writer.indexAllProducts();

        assertThat(written).isZero();
        verify(esClient, org.mockito.Mockito.never()).bulk(any(BulkRequest.class));
    }
}
