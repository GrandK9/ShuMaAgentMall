package com.shumamall.search.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import com.shumamall.search.index.ProductIndexService;
import com.shumamall.search.service.ProductIndexWriter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
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

    /** 全量重建互斥键。不设租期，由 Redisson 看门狗在任务存活期间续期。 */
    private static final String REBUILD_LOCK = "search:index:rebuild";

    private final ProductIndexWriter productIndexWriter;
    private final ProductIndexService productIndexService;
    private final ElasticsearchClient esClient;
    private final RedissonClient redissonClient;

    /**
     * 全量重建商品索引：新建物理索引灌库，完成后切换别名。
     *
     * @return 别名、当前物理索引、写入条数
     */
    @Operation(summary = "全量重建商品索引（新物理索引灌库后切换别名）")
    @PostMapping("/rebuild")
    public R<Map<String, Object>> rebuild() {
        RLock lock = redissonClient.getLock(REBUILD_LOCK);
        if (!acquire(lock)) {
            throw new BusinessException(ResultCode.CONFLICT, "索引正在重建");
        }
        try {
            int written = productIndexWriter.rebuildIndex();
            List<String> targets = productIndexService.aliasTargets();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("alias", ProductIndexService.PRODUCT_INDEX);
            result.put("indices", targets);
            result.put("written", written);
            log.info("管理端触发索引重建完成: indices={}, written={}", targets, written);
            return R.ok(result);
        } finally {
            release(lock);
        }
    }

    /**
     * 立即尝试加锁。看门狗在持锁线程还活着时续期，进程退出后锁自动释放。
     */
    private boolean acquire(RLock lock) {
        return lock.tryLock();
    }

    private void release(RLock lock) {
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    /**
     * 索引状态（是否存在 + 文档数）。
     *
     * @return 索引状态
     */
    @Operation(summary = "索引状态（别名、物理索引、文档数）")
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        boolean exists = productIndexService.nameExists();
        List<String> targets = productIndexService.aliasTargets();
        data.put("alias", ProductIndexService.PRODUCT_INDEX);
        data.put("exists", exists);
        data.put("indices", targets);
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
}
