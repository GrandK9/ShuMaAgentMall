package com.shumamall.agent.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.search.document.ProductDoc;
import com.shumamall.agent.search.dto.HybridSearchRequest;
import com.shumamall.agent.search.embedding.ProductEmbeddingService;
import com.shumamall.agent.search.service.ProductHybridSearchService;
import com.shumamall.agent.search.service.SearchFallbackService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语义混合检索工具：基于 ES 的 BM25 + KNN + RRF 做商品导购检索。
 * <p>
 * 与 {@link ProductSearchTool}（Feign 调商品服务）互补：
 * <ul>
 *   <li>Feign 搜索适合精确关键词、价格筛选、销量排序；</li>
 *   <li>Hybrid Search 适合自然语言语义（如"通勤降噪耳机""拍照好的手机"）。</li>
 * </ul>
 * 工具内部自动把用户 query 文本 embedding 后发起混合检索，返回精简字段 JSON 供 LLM 汇总。
 */
@Slf4j
@Component
public class HybridSearchTool extends BaseTool {

    private final ProductHybridSearchService hybridSearchService;
    private final ProductEmbeddingService embeddingService;
    private final SearchFallbackService fallbackService;
    private final ObjectMapper objectMapper;

    public HybridSearchTool(ProductHybridSearchService hybridSearchService,
                            ProductEmbeddingService embeddingService,
                            SearchFallbackService fallbackService,
                            ObjectMapper objectMapper) {
        this.hybridSearchService = hybridSearchService;
        this.embeddingService = embeddingService;
        this.fallbackService = fallbackService;
        this.objectMapper = objectMapper;
    }

    /**
     * 语义混合检索商品：BM25 关键词 + KNN 向量语义 + RRF 融合排序。
     *
     * @param query 用户原始导购描述，如"2000元内拍照好的手机"
     * @param topK  返回条数，默认 5，最大 20
     */
    @Tool(description = "基于语义混合检索查找商品，适合自然语言导购描述（如 2000元内拍照好的手机）")
    public String hybridSearchProduct(@ToolParam(description = "用户导购描述") String query,
                                      @ToolParam(description = "返回条数，默认5最多20") Integer topK,
                                      ToolContext toolContext) {
        try {
            int size = topK == null || topK <= 0 ? 5 : Math.min(topK, 20);
            List<Float> queryVector = embeddingService.embedQuery(query);
            HybridSearchRequest request = HybridSearchRequest.builder()
                    .keyword(query)
                    .queryVector(queryVector)
                    .topK(size)
                    .numCandidates(size * 5)
                    .size(size)
                    .build();
            List<ProductDoc> docs = hybridSearchService.search(request);
            if (docs.isEmpty()) {
                // 检索兜底：返回热销商品推荐
                List<ProductItemDTO> fallback = fallbackService.hotProducts(size);
                FallbackResult result = new FallbackResult();
                result.setFallback(true);
                result.setReason("未找到精确匹配的商品，为您推荐热销商品");
                result.setProducts(fallback.stream().map(this::toBriefFromDto).toList());
                return objectMapper.writeValueAsString(result);
            }
            return objectMapper.writeValueAsString(docs.stream()
                    .map(this::toBrief)
                    .toList());
        } catch (Exception e) {
            log.error("混合检索失败: query={}", query, e);
            return error(e);
        }
    }

    private ProductBrief toBrief(ProductDoc doc) {
        ProductBrief brief = new ProductBrief();
        brief.setId(doc.getId());
        brief.setName(doc.getName());
        brief.setSubtitle(doc.getSubtitle());
        brief.setBrand(doc.getBrand());
        brief.setCategory(doc.getCategory());
        brief.setPrice(doc.getPrice());
        brief.setStock(doc.getStock());
        brief.setSalesVolume(doc.getSalesVolume());
        return brief;
    }

    private ProductBrief toBriefFromDto(ProductItemDTO dto) {
        ProductBrief brief = new ProductBrief();
        brief.setId(dto.getId());
        brief.setName(dto.getName());
        brief.setSubtitle(dto.getSubtitle());
        brief.setPrice(dto.getPrice());
        brief.setStock(dto.getStock());
        brief.setSalesVolume(dto.getSalesVolume());
        return brief;
    }

    /**
     * 供 LLM 读取的精简商品视图（避免 embedding 等冗余字段占用上下文）。
     */
    public static class ProductBrief {
        private Long id;
        private String name;
        private String subtitle;
        private String brand;
        private String category;
        private java.math.BigDecimal price;
        private Integer stock;
        private Integer salesVolume;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public java.math.BigDecimal getPrice() { return price; }
        public void setPrice(java.math.BigDecimal price) { this.price = price; }
        public Integer getStock() { return stock; }
        public void setStock(Integer stock) { this.stock = stock; }
        public Integer getSalesVolume() { return salesVolume; }
        public void setSalesVolume(Integer salesVolume) { this.salesVolume = salesVolume; }
    }

    /**
     * 检索兜底结果包装。
     */
    public static class FallbackResult {
        private boolean fallback;
        private String reason;
        private List<ProductBrief> products;

        public boolean isFallback() { return fallback; }
        public void setFallback(boolean fallback) { this.fallback = fallback; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
        public List<ProductBrief> getProducts() { return products; }
        public void setProducts(List<ProductBrief> products) { this.products = products; }
    }
}
