package com.shumamall.dict.controller;

import com.shumamall.common.result.PageResult;
import com.shumamall.common.result.R;
import com.shumamall.dict.dto.DictSaveDTO;
import com.shumamall.dict.entity.DictEntity;
import com.shumamall.dict.service.DictService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 码表管理 API（管理端，新增/修改/删除/分页）。
 */
@Slf4j
@Tag(name = "字典-管理端", description = "管理端字典类型查询与字典项的分页查询、新增、更新、删除")
@RestController
@RequestMapping("/api/v1/admin/dict")
@RequiredArgsConstructor
public class DictAdminController {

    private final DictService dictService;

    /**
     * 查询全部字典类型（管理端下拉）。
     *
     * @return 类型列表
     */
    @GetMapping("/types")
    public R<List<DictEntity>> types() {
        return R.ok(dictService.listTypes());
    }

    /**
     * 分页查询字典项。
     *
     * @param type   类型编码（可选）
     * @param status 状态（可选）
     * @param page   页码
     * @param size   每页条数
     * @return 分页结果
     */
    @GetMapping
    public R<PageResult<DictEntity>> page(@RequestParam(required = false) String type,
                                          @RequestParam(required = false) Integer status,
                                          @RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return R.ok(dictService.pageQuery(type, status, page, size));
    }

    /**
     * 新增字典项。
     *
     * @param dto 字典项信息
     * @return 字典项 ID
     */
    @PostMapping
    public R<Long> create(@Valid @RequestBody DictSaveDTO dto) {
        return R.ok(dictService.create(dto));
    }

    /**
     * 更新字典项。
     *
     * @param id  字典项 ID
     * @param dto 字典项信息
     * @return 操作结果
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody DictSaveDTO dto) {
        dictService.update(id, dto);
        return R.ok();
    }

    /**
     * 删除字典项（含子项级联删除）。
     *
     * @param id 字典项 ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        dictService.delete(id);
        return R.ok();
    }
}
