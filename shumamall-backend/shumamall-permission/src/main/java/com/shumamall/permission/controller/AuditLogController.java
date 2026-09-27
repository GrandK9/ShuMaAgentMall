package com.shumamall.permission.controller;

import com.shumamall.common.perm.annotation.RequirePermission;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.permission.dto.AuditLogQuery;
import com.shumamall.permission.dto.AuditLogVO;
import com.shumamall.permission.service.AuditLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端操作审计控制器。
 * <p>
 * 只提供查询能力：审计记录由各业务服务的切面写入，管理端不允许修改或删除
 * （否则「审计」就失去了意义）。
 */
@Slf4j
@Tag(name = "权限-操作审计", description = "管理端分页查询操作审计日志，仅提供查询能力")
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询管理端操作审计日志。
     * <p>
     * 挂 {@code audit:view} 权限点：查询审计日志本身也是一次敏感操作，
     * 会被切面记录为一条审计（action=audit:view），形成「谁看了审计」的闭环。
     *
     * @param query 查询条件（操作人 / 权限编码 / 结果 / URI / 时间区间）
     * @return 分页结果
     */
    @GetMapping("/logs")
    @RequirePermission("audit:view")
    public R<PageResult<AuditLogVO>> queryLogs(AuditLogQuery query) {
        log.debug("查询审计日志: userId={}, action={}, result={}, page={}, size={}",
                query.getAdminUserId(), query.getAction(), query.getResult(),
                query.getPage(), query.getSize());
        return R.ok(auditLogService.query(query));
    }
}
