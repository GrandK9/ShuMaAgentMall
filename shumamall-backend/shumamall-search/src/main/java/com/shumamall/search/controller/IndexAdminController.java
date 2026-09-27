package com.shumamall.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.shumamall.common.result.R;
import com.shumamall.search.index.ProductIndexService;
import com.shumamall.search.service.ProductIndexWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 搜索索引管理控制器（管理端）。
 * <p>
 * 权限控制（@RequirePermission）待管理端鉴权链路完善后接入。
 */
@Tag(name = "搜索-索引管理", description = "管理端商品搜索索引的全量重建与状态查询接口")
@Slf4j
@RestController
@RequestMapping("/api/v1/search/index")
@RequiredArgsConstructor
public class IndexAdminController {

    private final ProductIndexWriter productIndexWriter;
    private final ProductIndexService productIndexService;
    private final ElasticsearchClient esClient;

    /**
     * 全量重建商品索引（删除 → 建 mapping → 拉取商品 → 批量写入）。
     *
     * @return 实际写入的商品数量
     */
    @Operation(summary = "全量重建商品索引（删除 → 建 mapping → 拉取商品 → 批量写入）")
    @PostMapping("/rebuild")
    public R<Integer> rebuild() {
        int written = productIndexWriter.rebuildIndex();
        log.info("管理端触发索引重建完成: written={}", written);
        return R.ok(written);
    }

    /**
     * 索引状态（是否存在 + 文档数）。
     *
     * @return 索引状态
     */
    @Operation(summary = "索引状态（是否存在 + 文档数）")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new HashMap<>();
        boolean exists = productIndexService.createIndexIfAbsent() || indexExists();
        data.put("index", ProductIndexService.PRODUCT_INDEX);
        data.put("exists", exists);
        if (exists) {
            try {
                long count = esClient.count(c -> c.index(ProductIndexService.PRODUCT_INDEX)).count();
                data.put("docCount", count);
            } catch (Exception e) {
                log.warn("查询索引文档数失败: err={}", e.getMessage());
                data.put("docCount", 0L);
            }
        }
        return R.ok(data);
    }

    private boolean indexExists() {
        try {
            return esClient.indices()
                    .exists(e -> e.index(ProductIndexService.PRODUCT_INDEX))
                    .value();
        } catch (Exception e) {
            return false;
        }
    }
}
