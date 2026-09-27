package com.shumamall.permission.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.shumamall.common.perm.audit.AuditLogDTO;
import com.shumamall.common.result.PageResult;
import com.shumamall.permission.dto.AuditLogQuery;
import com.shumamall.permission.dto.AuditLogVO;
import com.shumamall.permission.entity.AuditLogDocument;
import com.shumamall.permission.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理端操作审计服务实现（MongoDB）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** 单页最大条数，防止 size=100000 把整个集合拉回内存 */
    private static final int MAX_PAGE_SIZE = 100;

    private final MongoTemplate mongoTemplate;

    @Override
    public void save(AuditLogDTO dto) {
        AuditLogDocument document = new AuditLogDocument();
        document.setId(IdWorker.getId());
        document.setAdminUserId(dto.getAdminUserId());
        document.setAction(dto.getAction());
        document.setResource(dto.getResource());
        document.setMethod(dto.getMethod());
        document.setParams(dto.getParams());
        document.setResult(dto.getResult());
        document.setIp(dto.getIp());
        document.setOccurredAt(toLocalDateTime(dto.getOccurredAtEpochMilli()));
        document.setCostMs(dto.getCostMs());

        mongoTemplate.insert(document);
        log.debug("审计日志落库: id={}, userId={}, action={}, result={}",
                document.getId(), document.getAdminUserId(), document.getAction(), document.getResult());
    }

    @Override
    public PageResult<AuditLogVO> query(AuditLogQuery query) {
        Criteria criteria = buildCriteria(query);

        long page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        long size = query.getSize() == null || query.getSize() < 1
                ? 20 : Math.min(query.getSize(), MAX_PAGE_SIZE);

        Query pageQuery = new Query(criteria)
                // 审计列表按时间倒序：最近发生的操作排在最前
                .with(Sort.by(Sort.Direction.DESC, "occurredAt"))
                .skip((page - 1) * size)
                .limit((int) size);

        List<AuditLogDocument> documents = mongoTemplate.find(pageQuery, AuditLogDocument.class);
        long total = mongoTemplate.count(new Query(criteria), AuditLogDocument.class);

        List<AuditLogVO> records = documents.stream().map(this::toVO).collect(Collectors.toList());
        return new PageResult<>(page, size, total, records);
    }

    /**
     * 组装查询条件。所有条件之间是 AND 关系，未填写的条件不参与过滤。
     */
    private Criteria buildCriteria(AuditLogQuery query) {
        List<Criteria> conditions = new ArrayList<>();
        if (query.getAdminUserId() != null) {
            conditions.add(Criteria.where("adminUserId").is(query.getAdminUserId()));
        }
        if (StringUtils.hasText(query.getAction())) {
            conditions.add(Criteria.where("action").regex(Pattern.quote(query.getAction().trim())));
        }
        if (StringUtils.hasText(query.getResult())) {
            conditions.add(Criteria.where("result").is(query.getResult().trim()));
        }
        if (StringUtils.hasText(query.getResource())) {
            conditions.add(Criteria.where("resource").regex(Pattern.quote(query.getResource().trim())));
        }
        if (query.getStartTime() != null) {
            conditions.add(Criteria.where("occurredAt").gte(query.getStartTime()));
        }
        if (query.getEndTime() != null) {
            conditions.add(Criteria.where("occurredAt").lte(query.getEndTime()));
        }
        if (conditions.isEmpty()) {
            // 无条件时不能传空数组给 andOperator，否则生成的文档为空
            return new Criteria();
        }
        return new Criteria().andOperator(conditions.toArray(new Criteria[0]));
    }

    private AuditLogVO toVO(AuditLogDocument document) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(document.getId());
        vo.setAdminUserId(document.getAdminUserId());
        vo.setAction(document.getAction());
        vo.setResource(document.getResource());
        vo.setMethod(document.getMethod());
        vo.setParams(document.getParams());
        vo.setResult(document.getResult());
        vo.setIp(document.getIp());
        vo.setOccurredAt(document.getOccurredAt());
        vo.setCostMs(document.getCostMs());
        return vo;
    }

    /**
     * epoch 毫秒 → LocalDateTime（与 video / comment 模块的文档字段类型保持一致）。
     * <p>
     * 上报方必传时间；缺失时退回服务端当前时间，避免整条审计因时间为空而无法排序。
     */
    private static LocalDateTime toLocalDateTime(Long epochMilli) {
        if (epochMilli == null) {
            return LocalDateTime.now();
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMilli), ZoneId.systemDefault());
    }
}
