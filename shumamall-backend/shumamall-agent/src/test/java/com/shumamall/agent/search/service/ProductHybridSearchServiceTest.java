package com.shumamall.agent.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hybrid Search 服务单测（mock ES 客户端，不依赖真实 ES 环境）。
 * <p>
 * 验证目标：
 * <ol>
 *   <li>查询构建语法正确（index / multi_match / knn / rank-rrf / size）</li>
 *   <li>BM25 与 KNN 两路检索参数按请求正确下发</li>
 *   <li>ES 返回的 hits 能正确反序列化为商品文档列表</li>
 *   <li>异常/空结果降级返回空列表</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ProductHybridSearchServiceTest {

    @Mock
    private ElasticsearchClient esClient;

    private ProductHybridSearchService service;

    @BeforeEach
    void setUp() {
        service = new ProductHybridSearchService(esClient, 60, 50);
    }

    private ProductDoc doc(Long id, String name) {
        ProductDoc productDoc = new ProductDoc();
        productDoc.setId(id);
        productDoc.setName(name);
        return productDoc;
    }

    @Test
    void buildBm25Request_含关键词时_下发multiMatch并取窗口大小() {
        HybridSearchRequest req = HybridSearchRequest.builder()
                .keyword("拍照手机")
                .queryVector(List.of(0.1f, 0.2f, 0.3f))
                .topK(5)
                .numCandidates(100)
                .size(10)
                .build();

        SearchRequest bm25 = service.buildBm25Request(req);

        // 索引名
        assertThat(bm25.index()).containsExactly(ProductHybridSearchService.PRODUCT_INDEX);

        // BM25：multi_match 覆盖检索字段，名称加权最高
        assertThat(bm25.query().multiMatch().fields()).containsExactlyInAnyOrder(
                "name^3", "subtitle^2", "brand", "category", "description");
        assertThat(bm25.query().multiMatch().query()).isEqualTo("拍照手机");

        // 单路候选窗口 = windowSize（融合前每路取前 50 条）
        assertThat(bm25.size()).isEqualTo(50);
    }

    @Test
    void buildKnnRequest_下发embedding向量检索并保证候选数不小于k() {
        HybridSearchRequest req = HybridSearchRequest.builder()
                .keyword("拍照手机")
                .queryVector(List.of(0.1f, 0.2f, 0.3f))
                .topK(5)
                .numCandidates(100)
                .size(10)
                .build();

        SearchRequest knn = service.buildKnnRequest(req);

        assertThat(knn.index()).containsExactly(ProductHybridSearchService.PRODUCT_INDEX);
        assertThat(knn.knn()).hasSize(1);
        assertThat(knn.knn().get(0).field()).isEqualTo("embedding");
        assertThat(knn.knn().get(0).queryVector()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(knn.knn().get(0).k()).isEqualTo(50);
        // numCandidates 取 max(请求值, windowSize)，ES 要求候选数不小于 k
        assertThat(knn.knn().get(0).numCandidates()).isEqualTo(100);
    }

    @Test
    void buildKnnRequest_候选数小于窗口时_提升为窗口大小() {
        HybridSearchRequest req = HybridSearchRequest.builder()
                .keyword("手机")
                .queryVector(List.of(0.1f))
                .topK(3)
                .numCandidates(5)
                .size(3)
                .build();

        assertThat(service.buildKnnRequest(req).knn().get(0).numCandidates()).isEqualTo(50);
    }

    @Test
    void rrfFuse_两路排名倒数融合_双路命中者排最前() {
        ProductDoc doc1 = doc(1L, "A");
        ProductDoc doc2 = doc(2L, "B");
        ProductDoc doc3 = doc(3L, "C");

        // BM25 路：1 > 2；KNN 路：2 > 3 → 融合后 2（两路都命中）居首
        List<ProductDoc> fused = service.rrfFuse(List.of(
                List.of(doc1, doc2),
                List.of(doc2, doc3)), 3);

        assertThat(fused).extracting(ProductDoc::getId).containsExactly(2L, 1L, 3L);
    }

    @Test
    void rrfFuse_同一文档在两路重复出现时_分数累加且不重复返回() {
        ProductDoc doc1 = doc(1L, "A");

        List<ProductDoc> fused = service.rrfFuse(List.of(
                List.of(doc1),
                List.of(doc1)), 5);

        assertThat(fused).hasSize(1);
        assertThat(fused.get(0).getId()).isEqualTo(1L);
    }

    @Test
    void buildBm25Request_关键词为空时_query退化为matchAll() {
        HybridSearchRequest req = HybridSearchRequest.builder()
                .keyword("   ")
                .queryVector(List.of(0.1f))
                .build();

        assertThat(service.buildBm25Request(req).query().matchAll()).isNotNull();
    }

    @Test
    void search_解析hits并返回文档() throws Exception {
        ProductDoc doc = new ProductDoc();
        doc.setId(1L);
        doc.setName("小米14");
        doc.setPrice(new BigDecimal("3999"));

        SearchResponse<ProductDoc> resp = mock(SearchResponse.class);
        HitsMetadata<ProductDoc> hits = mock(HitsMetadata.class);
        Hit<ProductDoc> hit = mock(Hit.class);
        when(hit.source()).thenReturn(doc);
        when(hits.hits()).thenReturn(List.of(hit));
        when(resp.hits()).thenReturn(hits);
        when(esClient.search(any(SearchRequest.class), eq(ProductDoc.class))).thenReturn(resp);

        List<ProductDoc> result = service.search(HybridSearchRequest.builder()
                .keyword("手机")
                .queryVector(List.of(0.1f))
                .build());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getName()).isEqualTo("小米14");
    }

    @Test
    void search_hits为空时_返回空列表() throws Exception {
        SearchResponse<ProductDoc> resp = mock(SearchResponse.class);
        HitsMetadata<ProductDoc> hits = mock(HitsMetadata.class);
        when(hits.hits()).thenReturn(List.of());
        when(resp.hits()).thenReturn(hits);
        when(esClient.search(any(SearchRequest.class), eq(ProductDoc.class))).thenReturn(resp);

        List<ProductDoc> result = service.search(HybridSearchRequest.builder()
                .keyword("不存在")
                .queryVector(List.of(0.1f))
                .build());

        assertThat(result).isEmpty();
    }

    @Test
    void search_ES异常时_降级返回空列表不抛出() throws Exception {
        when(esClient.search(any(SearchRequest.class), eq(ProductDoc.class)))
                .thenThrow(new RuntimeException("connection refused"));

        List<ProductDoc> result = service.search(HybridSearchRequest.builder()
                .keyword("手机")
                .queryVector(List.of(0.1f))
                .build());

        assertThat(result).isEmpty();
    }
}
