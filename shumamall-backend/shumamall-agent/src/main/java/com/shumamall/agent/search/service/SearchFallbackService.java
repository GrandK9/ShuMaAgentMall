package com.shumamall.agent.search.service;

import com.shumamall.agent.dto.ProductItemDTO;
import com.shumamall.agent.feign.ProductFeignClient;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品检索兜底服务。
 * <p>
 * 当 ES 混合检索 / Feign 关键词搜索返回空或失败时，降级返回热销商品列表，
 * 保证 Agent 导购链路始终能给用户有效反馈，而不是"未找到"。
 */
@Slf4j
@Service
public class SearchFallbackService {

    private final ProductFeignClient productFeignClient;

    public SearchFallbackService(ProductFeignClient productFeignClient) {
        this.productFeignClient = productFeignClient;
    }

    /**
     * 获取热销商品兜底推荐。
     *
     * @param limit 返回条数，最大 20
     * @return 热销商品列表（调用失败返回空列表）
     */
    public List<ProductItemDTO> hotProducts(int limit) {
        int size = Math.min(Math.max(limit, 1), 20);
        try {
            R<PageResult<ProductItemDTO>> resp = productFeignClient.list(
                    1, size, null, null, null, 1, 1, null, null, "sales_volume", "desc");
            if (!resp.isSuccess() || resp.getData() == null || resp.getData().getRecords() == null) {
                return List.of();
            }
            return resp.getData().getRecords();
        } catch (Exception e) {
            log.error("兜底热销商品查询失败: err={}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 判断检索结果是否需要兜底：空列表 / null / 仅含错误标记。
     */
    public boolean needFallback(List<ProductItemDTO> results) {
        if (results == null || results.isEmpty()) {
            return true;
        }
        return false;
    }
}
