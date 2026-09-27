package com.shumamall.admin.controller;

import com.shumamall.admin.dto.UploadVO;
import com.shumamall.admin.service.UploadService;
import com.shumamall.common.result.R;
import com.shumamall.common.result.ResultCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传控制器。
 * <p>
 * 接收前端上传的文件，存储至 MinIO 并返回访问 URL。
 *
 * @author ShuMaMall Team
 */
@Slf4j
@Tag(name = "管理端-文件上传", description = "接收前端上传的文件，存储至 MinIO 并返回访问 URL")
@RestController
@RequestMapping("/api/v1/admin/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    /**
     * 上传文件。
     *
     * @param file 待上传的文件
     * @return 上传结果，包含文件访问 URL
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<UploadVO> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.failed(ResultCode.PARAM_ERROR, "上传文件不能为空");
        }
        log.info("Uploading file: name={}, size={}", file.getOriginalFilename(), file.getSize());
        UploadVO uploadVO = uploadService.upload(file);
        return R.ok(uploadVO);
    }
}
