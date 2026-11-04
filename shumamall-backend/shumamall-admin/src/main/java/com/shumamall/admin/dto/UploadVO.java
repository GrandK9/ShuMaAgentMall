package com.shumamall.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传结果视图对象。
 *
 * @author ShuMaMall Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadVO {

    /** 访问 URL：商品主图为公开直链；通用上传为短时签名 URL */
    private String fileUrl;

    /** MinIO 对象键（如 product/xxx.jpg） */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;
}
