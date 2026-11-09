package com.shumamall.permission.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.permission.dao.AuditLogMapper;
import com.shumamall.permission.dto.AuditLogQuery;
import com.shumamall.permission.dto.AuditLogVO;
import com.shumamall.permission.entity.AuditLogEntity;
import com.shumamall.permission.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理端操作审计（MySQL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditLogMapper auditLogMapper;

    @Override
    public void save(AuditLogDTO dto) {
        AuditLogEntity row = new AuditLogEntity();
        row.setId(IdWorker.getId());
        row.setAdminUserId(dto.getAdminUserId());
        row.setAction(dto.getAction());
        row.setResource(dto.getResource());
        row.setMethod(dto.getMethod());
        row.setParams(dto.getParams());
        row.setResult(dto.getResult());
        row.setIp(dto.getIp());
        row.setOccurredAt(toLocalDateTime(dto.getOccurredAtEpochMilli()));
        row.setCostMs(dto.getCostMs());

        auditLogMapper.insert(row);
        log.debug("审计日志落库: id={}, userId={}, action={}, result={}",
                row.getId(), row.getAdminUserId(), row.getAction(), row.getResult());
    }

    @Override
    public PageResult<AuditLogVO> query(AuditLogQuery query) {
        long pageNum = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() == null || query.getSize() < 1
                ? 20 : Math.min(query.getSize(), MAX_PAGE_SIZE);

        LambdaQueryWrapper<AuditLogEntity> wrapper = buildWrapper(query);
        wrapper.orderByDesc(AuditLogEntity::getOccurredAt);

        Page<AuditLogEntity> page = auditLogMapper.selectPage(new Page<>(pageNum, size), wrapper);
        List<AuditLogVO> records = page.getRecords().stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(pageNum, size, page.getTotal(), records);
    }

    private LambdaQueryWrapper<AuditLogEntity> buildWrapper(AuditLogQuery query) {
        LambdaQueryWrapper<AuditLogEntity> w = new LambdaQueryWrapper<>();
        if (query.getAdminUserId() != null) {
            w.eq(AuditLogEntity::getAdminUserId, query.getAdminUserId());
        }
        if (StringUtils.hasText(query.getAction())) {
            w.like(AuditLogEntity::getAction, query.getAction().trim());
        }
        if (StringUtils.hasText(query.getResult())) {
            w.eq(AuditLogEntity::getResult, query.getResult().trim());
        }
        if (StringUtils.hasText(query.getResource())) {
            w.like(AuditLogEntity::getResource, query.getResource().trim());
        }
        if (query.getStartTime() != null) {
            w.ge(AuditLogEntity::getOccurredAt, query.getStartTime());
        }
        if (query.getEndTime() != null) {
            w.le(AuditLogEntity::getOccurredAt, query.getEndTime());
        }
        return w;
    }

    private AuditLogVO toVO(AuditLogEntity row) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(row.getId());
        vo.setAdminUserId(row.getAdminUserId());
        vo.setAction(row.getAction());
        vo.setResource(row.getResource());
        vo.setMethod(row.getMethod());
        vo.setParams(row.getParams());
        vo.setResult(row.getResult());
        vo.setIp(row.getIp());
        vo.setOccurredAt(row.getOccurredAt());
        vo.setCostMs(row.getCostMs());
        return vo;
    }

    private static LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
