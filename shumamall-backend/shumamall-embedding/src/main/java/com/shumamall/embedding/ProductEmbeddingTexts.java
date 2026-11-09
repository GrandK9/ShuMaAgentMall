package com.shumamall.embedding;

/**
 * 商品向量化用文本拼接（文档侧不含 HTML 详情，与 Agent 灌库规则一致）。
 */
public final class ProductEmbeddingTexts {

    private ProductEmbeddingTexts() {
    }

    /**
     * 文档侧 embedding 输入：名称 + 副标题 + 品牌 + 分类。
     */
    public static String joinDocument(String name, String subtitle, String brandName, String categoryName) {
        StringBuilder sb = new StringBuilder();
        if (name != null && !name.isBlank()) {
            sb.append(name.trim());
        }
        if (subtitle != null && !subtitle.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(subtitle.trim());
        }
        if (brandName != null && !brandName.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(brandName.trim());
        }
        if (categoryName != null && !categoryName.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(categoryName.trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
