package com.knowledgecommunity.infrastructure.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.knowledgecommunity.config.OssConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

/**
 * OSS 服务：生成阿里云 OSS 预签名 URL / 代理上传
 * 前端通过预签名 URL 直传文件到 OSS，或通过后端代理上传
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssService {

    private final OssConfig ossConfig;

    /**
     * 生成预签名上传 URL（PUT 方式，1小时有效）
     * @param fileName 原始文件名
     * @return 包含 uploadUrl（预签名）和 objectUrl（访问地址）的数组
     */
    public String[] generatePresignedUrl(String fileName) {
        OSS ossClient = new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );

        try {
            // 生成唯一对象键，避免文件名冲突
            String ext = "";
            int dotIdx = fileName.lastIndexOf('.');
            if (dotIdx > 0) {
                ext = fileName.substring(dotIdx);
            }
            String objectKey = "uploads/" + UUID.randomUUID() + ext;
            Date expiration = new Date(System.currentTimeMillis() + 3600 * 1000);

            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                    ossConfig.getBucketName(), objectKey);
            request.setExpiration(expiration);
            request.setMethod(com.aliyun.oss.HttpMethod.PUT);
            // 设置 Content-Type，让前端上传时匹配
            String contentType = guessContentType(fileName);
            request.setContentType(contentType);

            URL url = ossClient.generatePresignedUrl(request);
            log.debug("生成预签名 URL: objectKey={}, contentType={}", objectKey, contentType);

            // 返回 [预签名上传URL, 对象访问URL]
            String objectUrl = ossConfig.getUrlPrefix() + objectKey;
            return new String[]{url.toString(), objectUrl};
        } finally {
            ossClient.shutdown();
        }
    }

    /**
     * 代理上传：后端接收文件并上传到 OSS
     * @param file 上传的文件
     * @return 文件访问 URL
     */
    public String uploadFile(MultipartFile file) {
        OSS ossClient = new OSSClientBuilder().build(
                ossConfig.getEndpoint(),
                ossConfig.getAccessKeyId(),
                ossConfig.getAccessKeySecret()
        );

        try {
            String originalName = file.getOriginalFilename();
            String ext = "";
            if (originalName != null && originalName.contains(".")) {
                ext = originalName.substring(originalName.lastIndexOf('.'));
            }
            String objectKey = "uploads/" + UUID.randomUUID() + ext;

            try (InputStream is = file.getInputStream()) {
                ossClient.putObject(ossConfig.getBucketName(), objectKey, is);
            }

            String objectUrl = ossConfig.getUrlPrefix() + objectKey;
            log.debug("文件上传成功: objectKey={}, objectUrl={}", objectKey, objectUrl);
            return objectUrl;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    /** 根据文件名猜测 Content-Type */
    private String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".mp4")) return "video/mp4";
        // 默认 jpeg
        return "image/jpeg";
    }

    /** 根据对象键获取完整访问 URL */
    public String getObjectUrl(String objectKey) {
        return ossConfig.getUrlPrefix() + objectKey;
    }
}
