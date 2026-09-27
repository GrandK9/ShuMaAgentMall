package com.shumamall.common.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 泛型树结构转换工具。
 * <p>
 * 任意 List&lt;T&gt; 只要提供 id / parentId / children 的提取函数，即可递归组装为树。
 * 适用于：码表树、权限菜单树、商品分类树等有父子关系的场景。
 */
public class TreeUtils {

    /**
     * 将扁平列表构建为树结构。
     *
     * @param list           原始列表
     * @param idGetter       ID 提取函数
     * @param parentIdGetter 父 ID 提取函数（null 或 0 视为根节点）
     * @param labelGetter    标签提取函数
     * @param childrenSetter children 设值函数
     * @param <T>            原始数据类型
     * @return 根节点列表
     */
    public static <T> List<TreeNode<T>> buildTree(List<T> list,
                                                   Function<T, Long> idGetter,
                                                   Function<T, Long> parentIdGetter,
                                                   Function<T, String> labelGetter,
                                                   BiConsumer<T, List<TreeNode<T>>> childrenSetter) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }

        // 按 parentId 分组
        Map<Long, List<T>> parentIdMap = list.stream()
                .collect(Collectors.groupingBy(item -> {
                    Long pid = parentIdGetter.apply(item);
                    return pid == null ? 0L : pid;
                }));

        // 转换为 TreeNode 并递归建立 children
        return list.stream()
                .filter(item -> {
                    Long pid = parentIdGetter.apply(item);
                    return pid == null || pid == 0;
                })
                .map(item -> buildNode(item, idGetter, parentIdGetter, labelGetter, parentIdMap))
                .collect(Collectors.toList());
    }

    private static <T> TreeNode<T> buildNode(T item,
                                              Function<T, Long> idGetter,
                                              Function<T, Long> parentIdGetter,
                                              Function<T, String> labelGetter,
                                              Map<Long, List<T>> parentIdMap) {
        TreeNode<T> node = new TreeNode<>();
        node.setId(idGetter.apply(item));
        node.setParentId(parentIdGetter.apply(item));
        node.setLabel(labelGetter.apply(item));
        node.setRaw(item);

        List<T> children = parentIdMap.get(idGetter.apply(item));
        if (children != null && !children.isEmpty()) {
            node.setChildren(children.stream()
                    .map(child -> buildNode(child, idGetter, parentIdGetter, labelGetter, parentIdMap))
                    .collect(Collectors.toList()));
        }

        return node;
    }

    /**
     * 树节点。
     *
     * @param <T> 原始数据类型
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TreeNode<T> {
        private Long id;
        private Long parentId;
        private String label;
        private String code;
        /** 原始数据兜底 */
        private T raw;
        private List<TreeNode<T>> children;
    }
}
