package com.shumamall.dict.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import com.shumamall.dict.dao.DictMapper;
import com.shumamall.dict.dto.DictSaveDTO;
import com.shumamall.dict.dto.DictVO;
import com.shumamall.dict.entity.DictEntity;
import com.shumamall.dict.service.DictService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 码表服务实现。
 * <p>
 * 查询链路：直接查 MySQL（数据量小）。后续量大可在 Redis 加缓存：
 * 启动时按 type 全量加载，写操作同步失效。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private final DictMapper dictMapper;

    @Override
    public List<DictVO> getItemsByType(String type) {
        List<DictEntity> entities = listEnabledByType(type);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<DictVO> getTreeByType(String type) {
        List<DictEntity> entities = listEnabledByType(type);
        if (CollectionUtils.isEmpty(entities)) {
            return new ArrayList<>();
        }
        Map<String, List<DictEntity>> byParent = entities.stream()
                .filter(e -> StringUtils.hasText(e.getParentCode()))
                .collect(Collectors.groupingBy(DictEntity::getParentCode));

        // 根节点：parentCode 为空
        List<DictEntity> roots = entities.stream()
                .filter(e -> !StringUtils.hasText(e.getParentCode()))
                .sorted(java.util.Comparator.comparing(e -> e.getSortOrder() == null ? 0 : e.getSortOrder()))
                .collect(Collectors.toList());

        List<DictVO> tree = new ArrayList<>();
        for (DictEntity root : roots) {
            tree.add(toTreeNode(root, byParent));
        }
        return tree;
    }

    @Override
    public List<DictEntity> listTypes() {
        // 按 type 去重（保留 sortOrder 最小的记录），返回 type + typeLabel
        LambdaQueryWrapper<DictEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DictEntity::getSortOrder);
        List<DictEntity> all = dictMapper.selectList(wrapper);
        return new ArrayList<>(all.stream()
                .collect(Collectors.toMap(DictEntity::getType, e -> e, (a, b) -> a, java.util.LinkedHashMap::new))
                .values());
    }

    @Override
    public PageResult<DictEntity> pageQuery(String type, Integer status, int page, int size) {
        LambdaQueryWrapper<DictEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(type), DictEntity::getType, type)
                .eq(status != null, DictEntity::getStatus, status)
                .orderByAsc(DictEntity::getType)
                .orderByAsc(DictEntity::getSortOrder);

        Page<DictEntity> result = dictMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getCurrent(), result.getSize(), result.getTotal(), result.getRecords());
    }

    @Override
    public Long create(DictSaveDTO dto) {
        DictEntity entity = new DictEntity();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        dictMapper.insert(entity);
        log.info("新增字典项: id={}, type={}, code={}", entity.getId(), entity.getType(), entity.getCode());
        return entity.getId();
    }

    @Override
    public void update(Long id, DictSaveDTO dto) {
        DictEntity existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        DictEntity entity = new DictEntity();
        BeanUtils.copyProperties(dto, entity);
        entity.setId(id);
        dictMapper.updateById(entity);
        log.info("更新字典项: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DictEntity existing = dictMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "字典项不存在");
        }
        // 级联删除子项
        dictMapper.delete(new LambdaQueryWrapper<DictEntity>()
                .eq(DictEntity::getParentCode, existing.getCode()));
        dictMapper.deleteById(id);
        log.info("删除字典项: id={}, code={}", id, existing.getCode());
    }

    // ==================== 私有方法 ====================

    /**
     * 按类型查询启用字典项。
     */
    private List<DictEntity> listEnabledByType(String type) {
        return dictMapper.selectList(new LambdaQueryWrapper<DictEntity>()
                .eq(DictEntity::getType, type)
                .eq(DictEntity::getStatus, 1)
                .orderByAsc(DictEntity::getSortOrder));
    }

    /**
     * 实体转平铺 VO。
     */
    private DictVO toVO(DictEntity entity) {
        DictVO vo = new DictVO();
        vo.setCode(entity.getCode());
        vo.setLabel(entity.getLabel());
        vo.setValue(entity.getValue());
        return vo;
    }

    /**
     * 递归构建树节点。
     */
    private DictVO toTreeNode(DictEntity entity, Map<String, List<DictEntity>> byParent) {
        DictVO vo = toVO(entity);
        List<DictEntity> children = byParent.get(entity.getCode());
        if (!CollectionUtils.isEmpty(children)) {
            children.sort(java.util.Comparator.comparing(e -> e.getSortOrder() == null ? 0 : e.getSortOrder()));
            vo.setChildren(children.stream()
                    .map(child -> toTreeNode(child, byParent))
                    .collect(Collectors.toList()));
        }
        return vo;
    }
}
