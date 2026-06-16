package com.knowledgecommunity.infrastructure.oss;

import com.knowledgecommunity.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * OSS 控制器：提供预签名 URL 和代理上传
 */
@Slf4j
@RestController
@RequestMapping("/api/oss")
@RequiredArgsConstructor
public class OssController {

    private final OssService ossService;

    /**
     * 获取预签名上传 URL
     * @param fileName 文件名
     * @return uploadUrl: 预签名上传地址, objectUrl: 上传后的访问地址
     */
    @GetMapping("/presign")
    public Result<Map<String, String>> presign(@RequestParam String fileName) {
        String[] urls = ossService.generatePresignedUrl(fileName);
        return Result.success(Map.of(
                "uploadUrl", urls[0],
                "objectUrl", urls[1]
        ));
    }

    /**
     * 代理上传：前端上传文件到后端，后端转存到 OSS
     * 避免 CORS 问题
     */
    @PostMapping("/upload")
    public Result<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }
        String objectUrl = ossService.uploadFile(file);
        return Result.success(Map.of("objectUrl", objectUrl));
    }
}
