package com.shumamall.agent.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.shumamall.agent.search.service.ProductIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品索引管理单测（mock ES 客户端，不依赖真实 ES 环境）。
 * <p>
 * 验证目标：
 * <ol>
 *   <li>索引已存在时跳过创建（幂等）</li>
 *   <li>索引不存在时创建带 dense_vector / text / keyword 映射的索引</li>
 *   <li>创建失败时降级返回 false 不抛出</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductIndexServiceTest {

    @Mock
    private ElasticsearchClient esClient;

    @Mock
    private ElasticsearchIndicesClient indicesClient;

    private ProductIndexService service;

    @BeforeEach
    void setUp() {
        service = new ProductIndexService(esClient);
    }

    @Test
    void createIndexIfAbsent_索引已存在时_跳过创建返回false() throws Exception {
        when(esClient.indices()).thenReturn(indicesClient);
        BooleanResponse existsResp = mock(BooleanResponse.class);
        when(existsResp.value()).thenReturn(true);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(existsResp);

        boolean result = service.createIndexIfAbsent();

        assertThat(result).isFalse();
        verify(indicesClient, never()).create(any(CreateIndexRequest.class));
    }

    @Test
    void createIndexIfAbsent_索引不存在时_创建含denseVector映射的索引() throws Exception {
        when(esClient.indices()).thenReturn(indicesClient);
        BooleanResponse existsResp = mock(BooleanResponse.class);
        when(existsResp.value()).thenReturn(false);
        when(indicesClient.exists(any(ExistsRequest.class))).thenReturn(existsResp);
        CreateIndexResponse createResp = mock(CreateIndexResponse.class);
        when(createResp.acknowledged()).thenReturn(true);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResp);

        boolean result = service.createIndexIfAbsent();

        assertThat(result).isTrue();

        // 捕获 CreateIndexRequest，断言 mapping 结构
        ArgumentCaptor<CreateIndexRequest> captor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest req = captor.getValue();

        assertThat(req.index()).isEqualTo(ProductIndexService.PRODUCT_INDEX);

        Map<String, Property> props = req.mappings().properties();
        assertThat(props).containsKeys("id", "name", "brand", "category", "price", "stock",
                "salesVolume", "description", "embedding");

        // 文本字段走自定义分析器（html_strip + ik_max_word）分词（BM25）
        assertThat(props.get("name").text().analyzer()).isEqualTo(ProductIndexService.TEXT_ANALYZER);
        // 品牌/分类：text 参与分词检索，另带 keyword 子字段保留精确过滤能力
        assertThat(props.get("brand").text().analyzer()).isEqualTo(ProductIndexService.TEXT_ANALYZER);
        assertThat(props.get("brand").text().fields()).containsKey("keyword");
        // 语义向量 dense_vector + cosine + HNSW 索引
        assertThat(props.get("embedding").denseVector().dims()).isEqualTo(ProductIndexService.EMBEDDING_DIMS);
        assertThat(props.get("embedding").denseVector().similarity()).isEqualTo(DenseVectorSimilarity.Cosine);
        assertThat(props.get("embedding").denseVector().index()).isTrue();
    }

    @Test
    void createIndexIfAbsent_ES异常时_降级返回false不抛出() throws Exception {
        when(esClient.indices()).thenReturn(indicesClient);
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenThrow(new RuntimeException("connection refused"));

        boolean result = service.createIndexIfAbsent();

        assertThat(result).isFalse();
    }
}
