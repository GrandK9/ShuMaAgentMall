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

    /** 文件访问 URL（MinIO 签名 URL，1 小时有效） */
    private String fileUrl;

    /** 上传后的文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;
}
