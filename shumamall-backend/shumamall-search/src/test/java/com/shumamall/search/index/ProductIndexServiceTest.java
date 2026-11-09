package com.shumamall.search.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.DenseVectorSimilarity;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商品搜索索引 mapping 单测（mock ES，不连真实集群）。
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
    void createPhysicalIndex_创建请求含embedding与nameNgram子字段() throws Exception {
        when(esClient.indices()).thenReturn(indicesClient);
        CreateIndexResponse createResp = mock(CreateIndexResponse.class);
        when(createResp.acknowledged()).thenReturn(true);
        when(indicesClient.create(any(CreateIndexRequest.class))).thenReturn(createResp);

        boolean ok = service.createPhysicalIndex("shumamall_product_search_v1", ProductIndexService.REFRESH_BULK);

        assertThat(ok).isTrue();
        ArgumentCaptor<CreateIndexRequest> captor = ArgumentCaptor.forClass(CreateIndexRequest.class);
        verify(indicesClient).create(captor.capture());
        CreateIndexRequest req = captor.getValue();

        Map<String, Property> props = req.mappings().properties();
        assertThat(props).containsKeys("id", "name", "brandName", "categoryName", "embedding");

        assertThat(props.get("name").text().analyzer()).isEqualTo(ProductIndexService.TEXT_ANALYZER);
        assertThat(props.get("name").text().searchAnalyzer()).isEqualTo(ProductIndexService.SEARCH_ANALYZER);
        assertThat(props.get("name").text().fields()).containsKey("ngram");

        assertThat(props.get("embedding").denseVector().dims()).isEqualTo(ProductIndexService.EMBEDDING_DIMS);
        assertThat(props.get("embedding").denseVector().similarity()).isEqualTo(DenseVectorSimilarity.Cosine);
        assertThat(props.get("embedding").denseVector().index()).isTrue();
    }
}
