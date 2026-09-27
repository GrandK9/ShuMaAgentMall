package com.shumamall.dict.service;

import com.shumamall.common.result.PageResult;
import com.shumamall.dict.dto.DictSaveDTO;
import com.shumamall.dict.dto.DictVO;
import com.shumamall.dict.entity.DictEntity;

import java.util.List;

/**
 * 码表服务接口。
 */
public interface DictService {

    /**
     * 按类型查询启用字典项（无层级）。
     *
     * @param type 字典类型编码
     * @return 字典项列表
     */
    List<DictVO> getItemsByType(String type);

    /**
     * 按类型查询字典树（基于 parentCode 构建层级）。
     *
     * @param type 字典类型编码
     * @return 树形字典项
     */
    List<DictVO> getTreeByType(String type);

    /**
     * 查询全部字典类型（管理端下拉用）。
     *
     * @return 类型列表（type + typeLabel 去重）
     */
    List<DictEntity> listTypes();

    /**
     * 分页查询字典项（管理端）。
     *
     * @param type   类型编码（可选）
     * @param status 状态（可选）
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    PageResult<DictEntity> pageQuery(String type, Integer status, int page, int size);

    /**
     * 新增字典项。
     *
     * @param dto 字典项信息
     * @return 字典项 ID
     */
    Long create(DictSaveDTO dto);

    /**
     * 更新字典项。
     *
     * @param id  字典项 ID
     * @param dto 字典项信息
     */
    void update(Long id, DictSaveDTO dto);

    /**
     * 删除字典项（含子项级联删除）。
     *
     * @param id 字典项 ID
     */
    void delete(Long id);
}
