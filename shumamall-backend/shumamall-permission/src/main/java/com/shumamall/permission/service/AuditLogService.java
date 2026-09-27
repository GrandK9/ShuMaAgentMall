package com.shumamall.permission.service;

import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.permission.dto.AuditLogQuery;
import com.shumamall.permission.dto.AuditLogVO;

/**
 * 管理端操作审计服务。
 */
public interface AuditLogService {

    /**
     * 落库一条审计记录（由各业务服务上报）。
     *
     * @param dto 审计记录
     */
    void save(AuditLogDTO dto);

    /**
     * 分页查询审计日志。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<AuditLogVO> query(AuditLogQuery query);
}
