package com.shumamall.admin.service;

import com.shumamall.admin.dto.UploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传服务接口。
 *
 * @author ShuMaMall Team
 */
public interface UploadService {

    /**
     * 上传文件到 MinIO。
     * <p>
     * 生成唯一文件名，上传至 MinIO 指定桶，并返回 1 小时有效的签名 URL。
     *
     * @param file 待上传文件
     * @return 上传结果视图对象
     */
    UploadVO upload(MultipartFile file);
}
