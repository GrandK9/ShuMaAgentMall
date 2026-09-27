package com.shumamall.product.service.impl;

import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.ResultCode;
import com.shumamall.product.dao.CategoryMapper;
import com.shumamall.product.dto.CategoryDTO;
import com.shumamall.product.entity.CategoryEntity;
import com.shumamall.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 商品分类服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryMapper categoryMapper;

    @Override
    public List<CategoryDTO> listTree() {
        List<CategoryEntity> all = categoryMapper.selectList(null);
        return buildTree(all);
    }

    /**
     * 将扁平分类列表构建为树形结构。
     *
     * @param entities 扁平分类列表
     * @return 树形分类列表
     */
    private List<CategoryDTO> buildTree(List<CategoryEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return new ArrayList<>();
        }

        // 转换为 DTO 并按 parentId 分组
        Map<Long, List<CategoryDTO>> parentIdMap = entities.stream()
                .map(this::toDTO)
                .collect(Collectors.groupingBy(
                        dto -> dto.getParentId() == null ? 0L : dto.getParentId()
                ));

        // 为每个非根节点设置 children
        for (CategoryDTO dto : parentIdMap.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList())) {
            Long id = dto.getId();
            if (parentIdMap.containsKey(id)) {
                dto.setChildren(parentIdMap.get(id));
            }
        }

        // 返回根节点（parentId 为 null 或 0）
        return entities.stream()
                .filter(e -> e.getParentId() == null || e.getParentId() == 0)
                .map(e -> {
                    CategoryDTO dto = toDTO(e);
                    Long id = dto.getId();
                    if (parentIdMap.containsKey(id)) {
                        dto.setChildren(parentIdMap.get(id));
                    }
                    return dto;
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(CategoryDTO categoryDTO) {
        CategoryEntity entity = new CategoryEntity();
        BeanUtils.copyProperties(categoryDTO, entity);

        if (entity.getParentId() == null) {
            entity.setParentId(0L);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }

        categoryMapper.insert(entity);
        log.info("分类创建成功: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(CategoryDTO categoryDTO) {
        CategoryEntity entity = categoryMapper.selectById(categoryDTO.getId());
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }

        BeanUtils.copyProperties(categoryDTO, entity);
        categoryMapper.updateById(entity);
        log.info("分类更新成功: id={}", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CategoryEntity entity = categoryMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }

        // 检查是否有子分类
        List<CategoryEntity> children = categoryMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CategoryEntity>()
                        .eq(CategoryEntity::getParentId, id));
        if (!children.isEmpty()) {
            throw new BusinessException(ResultCode.CONFLICT, "该分类下有子分类，无法删除");
        }

        categoryMapper.deleteById(id);
        log.info("分类删除成功: id={}", id);
    }

    private CategoryDTO toDTO(CategoryEntity entity) {
        CategoryDTO dto = new CategoryDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
