package com.shumamall.dict.controller;

import com.shumamall.common.result.R;
import com.shumamall.dict.dto.DictVO;
import com.shumamall.dict.service.DictService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 码表查询 API（用户端/各服务 Feign 调用，无需登录）。
 */
@Slf4j
@Tag(name = "字典-用户端", description = "用户端按类型查询启用字典项列表与字典树的只读接口")
@RestController
@RequestMapping("/api/v1/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    /**
     * 按类型查询启用字典项（平铺列表）。
     *
     * @param type 字典类型编码，如 product_spec
     * @return 字典项列表
     */
    @GetMapping("/items/{type}")
    public R<List<DictVO>> items(@PathVariable String type) {
        return R.ok(dictService.getItemsByType(type));
    }

    /**
     * 按类型查询字典树（基于 parentCode 构建层级）。
     *
     * @param type 字典类型编码，如 product_spec
     * @return 树形字典项
     */
    @GetMapping("/tree")
    public R<List<DictVO>> tree(@RequestParam String type) {
        return R.ok(dictService.getTreeByType(type));
    }
}
