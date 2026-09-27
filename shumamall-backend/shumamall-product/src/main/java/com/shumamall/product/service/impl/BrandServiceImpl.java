package com.shumamall.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shumamall.common.exception.BusinessException;
import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.ResultCode;
import com.shumamall.product.dao.BrandMapper;
import com.shumamall.product.dto.BrandDTO;
import com.shumamall.product.dto.PageQuery;
import com.shumamall.product.entity.BrandEntity;
import com.shumamall.product.service.BrandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 品牌服务实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandMapper brandMapper;

    @Override
    public PageResult<BrandDTO> list(PageQuery query) {
        Page<BrandEntity> page = new Page<>(query.getPage(), query.getSize());
        LambdaQueryWrapper<BrandEntity> wrapper = new LambdaQueryWrapper<BrandEntity>()
                .orderByAsc(BrandEntity::getSortOrder)
                .orderByDesc(BrandEntity::getCreatedAt);

        Page<BrandEntity> result = brandMapper.selectPage(page, wrapper);

        List<BrandDTO> list = result.getRecords().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return new PageResult<>(result.getCurrent(), result.getSize(),
                result.getTotal(), list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(BrandDTO brandDTO) {
        BrandEntity entity = new BrandEntity();
        BeanUtils.copyProperties(brandDTO, entity);

        if (entity.getStatus() == null) {
            entity.setStatus(1);
        }
        if (entity.getSortOrder() == null) {
            entity.setSortOrder(0);
        }

        brandMapper.insert(entity);
        log.info("品牌创建成功: id={}, name={}", entity.getId(), entity.getName());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(BrandDTO brandDTO) {
        BrandEntity entity = brandMapper.selectById(brandDTO.getId());
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }

        BeanUtils.copyProperties(brandDTO, entity);
        brandMapper.updateById(entity);
        log.info("品牌更新成功: id={}", entity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        BrandEntity entity = brandMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "品牌不存在");
        }

        brandMapper.deleteById(id);
        log.info("品牌删除成功: id={}", id);
    }

    private BrandDTO toDTO(BrandEntity entity) {
        BrandDTO dto = new BrandDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }
}
